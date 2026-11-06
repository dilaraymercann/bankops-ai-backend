package com.bankops.application.ai;

import com.bankops.application.CurrentUser;
import com.bankops.application.ResourceNotFoundException;
import com.bankops.application.audit.AuditDetails;
import com.bankops.application.audit.AuditService;
import com.bankops.application.repository.AiAnalysisRepository;
import com.bankops.application.repository.ExceptionCaseRepository;
import com.bankops.application.repository.PaymentTransactionRepository;
import com.bankops.domain.AiAnalysis;
import com.bankops.domain.AiFeedback;
import com.bankops.domain.CaseAccessPolicy;
import com.bankops.domain.ErrorCode;
import com.bankops.domain.ExceptionCase;
import com.bankops.domain.PaymentTransaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * AI analiz akışı (CLAUDE.md §10): önbellek → maskeleme → hibrit benzerlik araması → (kanıt yoksa fallback) → LLM →
 * doğrulama → kayıt. Dış API çağrıları bir veritabanı transaction'ının içinde yapılmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmAnalysisService {

    static final String FALLBACK_MESSAGE = "Güvenilir geçmiş kayıt bulunamadı. Bu kayıt için AI önerisi üretilmedi.";
    private static final String SEED_MODEL = "synthetic-seed";

    private static final String SYSTEM_PROMPT = """
            Sen bir banka ödeme operasyonları ekibine yardım eden karar destek asistanısın.
            Kurallar:
            - Yalnızca öneri sunarsın; kesin karar vermez, hiçbir işlemi kendin yapmazsın.
            - Yalnızca sana verilen "geçmiş doğrulanmış kayıtlar"a dayanırsın. Dışarıdan bilgi uydurma.
            - Kanıt yetersizse bunu açıkça belirt ve tahmin yürütme.
            - Metinlerdeki [IBAN], [TCKN], [GÖNDEREN], [ALICI], [TELEFON] ifadeleri gizlenmiş kişisel verilerdir; bunları tahmin etmeye çalışma.
            - Yanıtın Türkçe olmalı ve yalnızca istenen JSON biçiminde olmalı.
            - referencedCaseIds ve supportingCaseIds içine yalnızca sana verilen kayıt numaralarını yaz.
            """;

    private static final String OUTPUT_FORMAT = """
            Yanıtı yalnızca şu JSON biçiminde ver:
            {
              "summary": "Problemin anlaşılır açıklaması",
              "possibleCauses": [{"cause": "olası neden", "supportingCaseIds": ["EXC-..."]}],
              "recommendedSteps": ["önerilen inceleme adımı"],
              "warnings": ["dikkat edilmesi gereken nokta"],
              "referencedCaseIds": ["EXC-..."]
            }
            """;

    private final ExceptionCaseRepository caseRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final AiAnalysisRepository analysisRepository;
    private final CaseAccessPolicy accessPolicy;
    private final TextEmbedder embedder;
    private final LlmClient llm;
    private final SimilaritySearch similaritySearch;
    private final PiiMasker masker;
    private final AnalysisRateLimiter rateLimiter;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final AiProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    /** Kayıt için hâlâ geçerli (24 saatten yeni ve kayıt değişmemiş) bir analiz varsa döner; yoksa boş. */
    @Transactional(readOnly = true)
    public Optional<AiAnalysisView> latest(Long caseId, CurrentUser user) {
        ExceptionCase exceptionCase = accessibleCase(caseId, user);
        return freshAnalysis(exceptionCase).map(analysis -> toView(analysis, true));
    }

    /** Analiz üretir ya da önbellekten getirir. {@code refresh = true} önbelleği atlar. */
    public AiAnalysisView analyze(Long caseId, boolean refresh, CurrentUser user) {
        ExceptionCase exceptionCase = accessibleCase(caseId, user);
        if (!refresh) {
            Optional<AiAnalysis> cached = freshAnalysis(exceptionCase);
            if (cached.isPresent()) {
                return toView(cached.get(), true);
            }
        }
        rateLimiter.acquire(user.id());
        if (!embedder.isConfigured() || !llm.isConfigured()) {
            throw new AiUnavailableException("AI servisi yapılandırılmamış (GEMINI_API_KEY tanımlı değil)");
        }
        PaymentTransaction transaction = transactionRepository.findById(exceptionCase.getTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("İşlem bulunamadı"));
        long startedAt = System.nanoTime();

        // 1) Metni maskele: modele ve embedding servisine kişisel veri hiç gitmez
        PiiMasker.Result errorText = masker.mask(exceptionCase.getErrorDescription(), transaction.getSenderName(), transaction.getReceiverName());
        PiiMasker.Result descriptionText = masker.mask(transaction.getDescription(), transaction.getSenderName(), transaction.getReceiverName());
        int maskedCount = errorText.maskedCount() + descriptionText.maskedCount();

        // 2) Hibrit benzerlik araması
        float[] query = embedder.embedQuery("Hata: " + errorText.text() + "\nİşlem: " + descriptionText.text());
        List<SimilarResolution> matches = similaritySearch.search(query, exceptionCase.getErrorCode(), transaction.getType(), caseId);

        // 3) Fallback: güvenilir kanıt yoksa model hiç çağrılmaz
        if (matches.isEmpty()) {
            return new AiAnalysisView(null, false, FALLBACK_MESSAGE, null, List.of(), 0, maskedCount, null, clock.instant(), null, false);
        }

        // 4) Model çağrısı; geçmiş çözüm metinleri de maskelenir
        List<String> maskedResolutions = new ArrayList<>();
        for (SimilarResolution match : matches) {
            PiiMasker.Result masked = masker.mask(match.text(), transaction.getSenderName(), transaction.getReceiverName());
            maskedCount += masked.maskedCount();
            maskedResolutions.add(masked.text());
        }
        String userPrompt = buildUserPrompt(exceptionCase, transaction, errorText.text(), descriptionText.text(), matches, maskedResolutions);
        LlmClient.Reply reply = callWithRetry(userPrompt);
        AiAnalysisResult result = parse(reply.text());

        // 5) Halüsinasyon kontrolü: getirilmemiş kayıt numaraları yanıttan çıkarılır
        AiAnalysisResult checked = keepOnlyRetrievedReferences(result, matches);

        // 6) Kayıt + audit tek transaction'da
        int latencyMs = (int) Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        int maskedTotal = maskedCount;
        AiAnalysis saved = transactions.execute(status -> persist(exceptionCase, checked, matches, maskedTotal, reply.modelName(), latencyMs, user));
        return toView(saved, false);
    }

    /** Uzmanın "Faydalı / Faydalı değil" geri bildirimi; dashboard'daki AI kabul oranını besler. */
    @Transactional
    public AiAnalysisView giveFeedback(Long analysisId, AiFeedback feedback, CurrentUser user) {
        AiAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analiz bulunamadı"));
        accessibleCase(analysis.getCaseId(), user);
        analysis.setFeedback(feedback);
        auditService.record(user.id(), "AI_FEEDBACK", "AiAnalysis", analysisId, AuditDetails.of("feedback", feedback));
        return toView(analysis, true);
    }

    // ---------------------------------------------------------------- yardımcılar

    private ExceptionCase accessibleCase(Long caseId, CurrentUser user) {
        ExceptionCase exceptionCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Kayıt bulunamadı"));
        accessPolicy.assertCanView(user.role(), user.id(), exceptionCase.getAssignedToUserId());
        return exceptionCase;
    }

    /** Önbellek kuralı: son {@code cache-hours} içinde üretilmiş ve kayıt o zamandan beri değişmemiş analiz. */
    private Optional<AiAnalysis> freshAnalysis(ExceptionCase exceptionCase) {
        Instant oldest = clock.instant().minus(Duration.ofHours(properties.cacheHours()));
        return analysisRepository.findFirstByCaseIdAndModelNameNotOrderByCreatedAtDesc(exceptionCase.getId(), SEED_MODEL)
                .filter(analysis -> !analysis.getCreatedAt().isBefore(oldest))
                .filter(analysis -> !analysis.getCreatedAt().isBefore(exceptionCase.getUpdatedAt()));
    }

    private static String buildUserPrompt(ExceptionCase exceptionCase, PaymentTransaction transaction, String errorText,
                                          String descriptionText, List<SimilarResolution> matches, List<String> maskedResolutions) {
        StringBuilder prompt = new StringBuilder("Analiz edilecek kayıt:\n");
        prompt.append("- Hata kodu: ").append(exceptionCase.getErrorCode()).append('\n');
        prompt.append("- Hata açıklaması: ").append(errorText).append('\n');
        prompt.append("- İşlem tipi: ").append(transaction.getType()).append('\n');
        prompt.append("- Tutar: ").append(transaction.getAmount().toPlainString()).append(' ').append(transaction.getCurrency()).append('\n');
        prompt.append("- İşlem açıklaması: ").append(descriptionText == null || descriptionText.isBlank() ? "(yok)" : descriptionText).append("\n\n");
        prompt.append("Geçmiş doğrulanmış kayıtlar (yalnızca bunlara dayan):\n");
        for (int i = 0; i < matches.size(); i++) {
            SimilarResolution match = matches.get(i);
            prompt.append(i + 1).append(". [").append(match.caseNumber()).append("] benzerlik %.2f".formatted(match.similarity()))
                    .append(", ").append(match.matchReason()).append('\n')
                    .append("   Uygulanan çözüm: ").append(maskedResolutions.get(i)).append('\n');
        }
        return prompt.append('\n').append(OUTPUT_FORMAT).toString();
    }

    /** Model geçersiz JSON üretirse bir kez daha dener (CLAUDE.md §10.6); iki denemede de olmazsa hata döner. */
    private LlmClient.Reply callWithRetry(String userPrompt) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            LlmClient.Reply reply = llm.generateJson(SYSTEM_PROMPT, userPrompt);
            if (tryParse(reply.text()) != null) {
                return reply;
            }
            log.warn("Model şemaya uymayan yanıt üretti (deneme {}/2)", attempt);
        }
        throw new AiAnalysisFailedException("Model geçerli bir analiz üretemedi, lütfen tekrar deneyin");
    }

    private AiAnalysisResult parse(String text) {
        return tryParse(text);
    }

    /** Geçerli bir analiz döner; JSON bozuksa ya da özet boşsa {@code null}. */
    private AiAnalysisResult tryParse(String text) {
        try {
            AiAnalysisResult result = json.readValue(stripCodeFence(text), AiAnalysisResult.class);
            return result.summary() == null || result.summary().isBlank() ? null : result;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    /** Yanıttaki her kayıt numarası gerçekten getirilen kayıtlar arasında olmalıdır; olmayanlar çıkarılır ve loglanır. */
    private static AiAnalysisResult keepOnlyRetrievedReferences(AiAnalysisResult result, List<SimilarResolution> matches) {
        Set<String> allowed = matches.stream().map(SimilarResolution::caseNumber).collect(Collectors.toSet());
        Set<String> dropped = new HashSet<>();
        List<String> referenced = filter(result.referencedCaseIds(), allowed, dropped);
        List<AiAnalysisResult.PossibleCause> causes = result.possibleCauses().stream()
                .map(cause -> new AiAnalysisResult.PossibleCause(
                        cause.cause(), filter(cause.supportingCaseIds() == null ? List.of() : cause.supportingCaseIds(), allowed, dropped)))
                .toList();
        if (!dropped.isEmpty()) {
            log.warn("Modelin uydurduğu kayıt numaraları yanıttan çıkarıldı: {}", dropped);
        }
        return new AiAnalysisResult(result.summary(), causes, result.recommendedSteps(), result.warnings(), referenced);
    }

    private static List<String> filter(List<String> ids, Set<String> allowed, Set<String> dropped) {
        List<String> kept = new ArrayList<>();
        for (String id : ids) {
            if (allowed.contains(id)) {
                if (!kept.contains(id)) {
                    kept.add(id);
                }
            } else {
                dropped.add(id);
            }
        }
        return kept;
    }

    private AiAnalysis persist(ExceptionCase exceptionCase, AiAnalysisResult result, List<SimilarResolution> matches,
                               int maskedCount, String modelName, int latencyMs, CurrentUser user) {
        // result_json: modelin yanıtı + arayüzün göstereceği benzerlik skorları (seed analizlerinde skor yoktur)
        ObjectNode node = json.valueToTree(result);
        node.set("referenceScores", json.valueToTree(matches.stream()
                .map(match -> Map.of("caseNumber", match.caseNumber(), "similarity", match.similarity(), "matchReason", match.matchReason()))
                .toList()));
        node.put("similarRecordCount", matches.size());

        AiAnalysis analysis = new AiAnalysis();
        analysis.setCaseId(exceptionCase.getId());
        analysis.setResultJson(json.writeValueAsString(node));
        analysis.setReferencedCaseIds(result.referencedCaseIds());
        analysis.setMaskedFieldCount(maskedCount);
        analysis.setModelName(modelName);
        analysis.setLatencyMs(latencyMs);
        analysis.setCreatedAt(clock.instant());
        analysisRepository.save(analysis);

        auditService.record(user.id(), "AI_ANALYZE", "ExceptionCase", exceptionCase.getId(),
                AuditDetails.of("analysisId", analysis.getId(), "referencedCaseIds", String.join(",", result.referencedCaseIds()),
                        "maskedFieldCount", maskedCount, "model", modelName));
        return analysis;
    }

    /** Kayıtlı analizi arayüz biçimine çevirir; referans kayıtların çözüm metni veritabanından okunur. */
    private AiAnalysisView toView(AiAnalysis analysis, boolean cached) {
        JsonNode node = json.readTree(analysis.getResultJson());
        AiAnalysisResult result = json.treeToValue(node, AiAnalysisResult.class);

        Map<String, JsonNode> scores = new LinkedHashMap<>();
        for (JsonNode score : node.path("referenceScores")) {
            scores.put(score.path("caseNumber").asString(""), score);
        }
        List<AiReference> references = referencesOf(analysis.getReferencedCaseIds(), scores);
        int similarCount = node.has("similarRecordCount") ? node.path("similarRecordCount").asInt() : analysis.getReferencedCaseIds().size();

        return new AiAnalysisView(analysis.getId(), true, null, result, references, similarCount,
                analysis.getMaskedFieldCount(), analysis.getModelName(), analysis.getCreatedAt(), analysis.getFeedback(), cached);
    }

    private List<AiReference> referencesOf(List<String> caseNumbers, Map<String, JsonNode> scores) {
        if (caseNumbers.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", caseNumbers.stream().map(number -> "?").toList());
        Map<String, AiReference> byNumber = new LinkedHashMap<>();
        jdbc.query("""
                SELECT c.case_number, c.error_code, r.text
                FROM resolutions r JOIN exception_cases c ON c.id = r.case_id
                WHERE r.is_verified = TRUE AND c.case_number IN (%s)
                """.formatted(placeholders), row -> {
            String number = row.getString("case_number");
            JsonNode score = scores.get(number);
            byNumber.put(number, new AiReference(number, ErrorCode.valueOf(row.getString("error_code")),
                    score == null ? null : score.path("similarity").asDouble(),
                    score == null ? null : score.path("matchReason").asString(null),
                    row.getString("text")));
        }, caseNumbers.toArray());
        return caseNumbers.stream().map(byNumber::get).filter(java.util.Objects::nonNull).toList();
    }
}
