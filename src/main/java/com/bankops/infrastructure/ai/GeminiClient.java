package com.bankops.infrastructure.ai;

import com.bankops.application.ai.AiProperties;
import com.bankops.application.ai.AiUnavailableException;
import com.bankops.application.ai.LlmClient;
import com.bankops.application.ai.TextEmbedder;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google Gemini REST API istemcisi: hem sohbet ({@link LlmClient}) hem embedding ({@link TextEmbedder}).
 * İki uç nokta için küçük bir RestClient yeterli olduğundan ayrı bir AI kütüphanesi eklenmedi.
 */
@Slf4j
@Component
public class GeminiClient implements LlmClient, TextEmbedder {

    private static final int MAX_ATTEMPTS = 3;
    /** Ücretsiz katman dakikada 100 embedding isteğine izin verir ve toplu çağrıdaki her metin bir istek sayılır. */
    private static final int EMBED_BATCH_SIZE = 50;
    /** Kullanıcı bir yanıt beklerken en fazla bu kadar beklenir; toplu (arka plan) işte kota dolunca çok daha uzun beklenir. */
    private static final Duration INTERACTIVE_MAX_WAIT = Duration.ofSeconds(5);
    private static final Duration BATCH_MAX_WAIT = Duration.ofSeconds(70);
    private static final int BATCH_MAX_ATTEMPTS = 8;
    private static final Pattern RETRY_IN_SECONDS = Pattern.compile("retry in ([0-9.]+)s");

    private final AiProperties.Gemini config;
    private final JsonMapper json;
    private final RestClient http;

    public GeminiClient(AiProperties properties, JsonMapper json) {
        this.config = properties.gemini();
        this.json = json;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        this.http = RestClient.builder().baseUrl(config.baseUrl()).requestFactory(requestFactory).build();
    }

    @Override
    public boolean isConfigured() {
        return config.configured();
    }

    // ---------------------------------------------------------------- sohbet

    @Override
    public LlmClient.Reply generateJson(String systemPrompt, String userPrompt) {
        requireConfigured();
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0.2, "maxOutputTokens", 8192));

        AiUnavailableException last = null;
        for (String model : config.chatModels()) {
            try {
                JsonNode response = post("/models/" + model + ":generateContent", body, INTERACTIVE_MAX_WAIT, MAX_ATTEMPTS);
                return new LlmClient.Reply(textOf(response), model);
            } catch (AiUnavailableException e) {
                log.warn("Gemini sohbet modeli yanıt vermedi ({}): {}", model, e.getMessage());
                last = e;
            }
        }
        throw new AiUnavailableException("AI servisi şu an yanıt vermiyor, biraz sonra tekrar deneyin", last);
    }

    private static String textOf(JsonNode response) {
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            // Düşünme özetleri ("thought": true) yanıt metnine karışmasın
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asString(""));
            }
        }
        if (text.isEmpty()) {
            throw new AiUnavailableException("Model boş yanıt döndü");
        }
        return text.toString();
    }

    // ---------------------------------------------------------------- embedding

    @Override
    public float[] embedQuery(String text) {
        requireConfigured();
        Map<String, Object> body = embedRequest(text, "RETRIEVAL_QUERY");
        JsonNode response = post("/models/" + config.embeddingModel() + ":embedContent", body, INTERACTIVE_MAX_WAIT, MAX_ATTEMPTS);
        return vectorOf(response.path("embedding").path("values"));
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        requireConfigured();
        List<float[]> vectors = new ArrayList<>();
        for (int from = 0; from < texts.size(); from += EMBED_BATCH_SIZE) {
            List<String> batch = texts.subList(from, Math.min(from + EMBED_BATCH_SIZE, texts.size()));
            Map<String, Object> body = Map.of("requests", batch.stream().map(text -> embedRequest(text, "RETRIEVAL_DOCUMENT")).toList());
            JsonNode response = post("/models/" + config.embeddingModel() + ":batchEmbedContents", body, BATCH_MAX_WAIT, BATCH_MAX_ATTEMPTS);
            JsonNode embeddings = response.path("embeddings");
            if (embeddings.size() != batch.size()) {
                throw new AiUnavailableException("Embedding yanıtı beklenen sayıda değil");
            }
            for (JsonNode embedding : embeddings) {
                vectors.add(vectorOf(embedding.path("values")));
            }
        }
        return vectors;
    }

    private Map<String, Object> embedRequest(String text, String taskType) {
        return Map.of(
                "model", "models/" + config.embeddingModel(),
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "taskType", taskType,
                "outputDimensionality", config.embeddingDimensions());
    }

    private float[] vectorOf(JsonNode values) {
        if (values.size() != config.embeddingDimensions()) {
            throw new AiUnavailableException("Embedding boyutu beklenenden farklı: " + values.size());
        }
        float[] vector = new float[values.size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = values.get(i).floatValue();
        }
        return vector;
    }

    // ---------------------------------------------------------------- ortak HTTP

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new AiUnavailableException("AI servisi yapılandırılmamış (GEMINI_API_KEY tanımlı değil)");
        }
    }

    /**
     * POST gönderir; geçici hatalarda (429, 5xx) bekleyip yeniden dener. Kota hatasında Google'ın bildirdiği süre
     * ("Please retry in 7.9s") kadar beklenir, ancak {@code maxWait}'ten uzun beklemek gerekiyorsa vazgeçilir.
     */
    private JsonNode post(String path, Map<String, Object> body, Duration maxWait, int maxAttempts) {
        String payload = json.writeValueAsString(body);
        for (int attempt = 1; ; attempt++) {
            try {
                String response = http.post()
                        .uri(path)
                        .header("x-goog-api-key", config.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(String.class);
                return json.readTree(response);
            } catch (HttpStatusCodeException e) {
                boolean transientError = e.getStatusCode().value() == 429 || e.getStatusCode().is5xxServerError();
                Duration wait = waitBeforeRetry(e, attempt);
                if (!transientError || attempt == maxAttempts || wait.compareTo(maxWait) > 0) {
                    // Yanıt gövdesi (API anahtarı içermez) tanı için kısaltılarak loglanır
                    log.warn("Gemini hatası {}: {}", e.getStatusCode().value(), abbreviate(e.getResponseBodyAsString()));
                    throw new AiUnavailableException("AI servisi hata döndürdü (" + e.getStatusCode().value() + ")", e);
                }
                log.info("Gemini {} döndürdü, {} sn beklenip yeniden denenecek", e.getStatusCode().value(), wait.toSeconds());
                sleep(wait.toMillis());
            } catch (RuntimeException e) {
                if (attempt == maxAttempts) {
                    throw new AiUnavailableException("AI servisine ulaşılamadı", e);
                }
                sleep(attempt * 700L);
            }
        }
    }

    private static Duration waitBeforeRetry(HttpStatusCodeException e, int attempt) {
        Matcher hint = RETRY_IN_SECONDS.matcher(e.getResponseBodyAsString());
        if (e.getStatusCode().value() == 429 && hint.find()) {
            return Duration.ofMillis((long) (Double.parseDouble(hint.group(1)) * 1000) + 1000);
        }
        return Duration.ofMillis(attempt * 700L);
    }

    private static String abbreviate(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("İstek yarıda kesildi");
        }
    }
}
