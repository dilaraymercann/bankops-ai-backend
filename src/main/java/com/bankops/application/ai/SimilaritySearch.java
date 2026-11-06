package com.bankops.application.ai;

import com.bankops.domain.ErrorCode;
import com.bankops.domain.TransactionType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibrit benzerlik araması (CLAUDE.md §10.2): önce yapısal süzgeç (aynı hata kodu, yalnızca doğrulanmış ve bilgi
 * tabanına alınmış çözümler), sonra embedding'e göre kosinüs sıralaması. Sorgu okunabilirlik için native SQL'dir.
 */
@Component
@RequiredArgsConstructor
public class SimilaritySearch {

    // <=> pgvector'ün kosinüs MESAFESİ operatörüdür (0 = aynı); benzerlik = 1 - mesafe.
    private static final String SQL = """
            SELECT r.id, r.case_id, c.case_number, c.error_code, t.type, r.text,
                   1 - (r.embedding <=> ?::vector) AS similarity
            FROM resolutions r
            JOIN exception_cases c ON c.id = r.case_id
            JOIN payment_transactions t ON t.id = c.transaction_id
            WHERE r.is_verified = TRUE
              AND r.include_in_knowledge_base = TRUE
              AND r.embedding IS NOT NULL
              AND c.error_code = ?
              AND r.case_id <> ?
            ORDER BY r.embedding <=> ?::vector
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final AiProperties properties;

    /** Aynı metni taşıyan çözümlerin tekrar tekrar gelmemesi için sorgu, istenenden fazla satır çeker. */
    private static final int OVER_FETCH = 4;

    /**
     * En benzer çözümleri döner; benzerliği eşiğin ({@code similarity-threshold}) altında kalanlar elenir.
     * Bilgi tabanında birebir aynı çözüm metni çok kayıtta geçtiği için aynı metin yalnızca bir kez döner.
     */
    public List<SimilarResolution> search(float[] queryEmbedding, ErrorCode errorCode, TransactionType type, Long excludeCaseId) {
        String vector = VectorLiteral.of(queryEmbedding);
        return jdbc.query(SQL, (row, index) -> {
                    TransactionType resultType = TransactionType.valueOf(row.getString("type"));
                    return new SimilarResolution(
                            row.getLong("id"), row.getLong("case_id"), row.getString("case_number"),
                            ErrorCode.valueOf(row.getString("error_code")), resultType, row.getString("text"),
                            row.getDouble("similarity"), matchReason(resultType, type));
                },
                vector, errorCode.name(), excludeCaseId, vector, properties.maxReferences() * OVER_FETCH)
                .stream()
                .filter(match -> match.similarity() >= properties.similarityThreshold())
                .filter(distinctByText())
                .limit(properties.maxReferences())
                .toList();
    }

    private static Predicate<SimilarResolution> distinctByText() {
        Set<String> seen = new HashSet<>();
        return match -> seen.add(match.text());
    }

    /** Arayüzde gösterilen eşleşme nedeni, örn. "aynı hata kodu · FAST". */
    private static String matchReason(TransactionType found, TransactionType wanted) {
        return found == wanted ? "aynı hata kodu · " + typeLabel(found) : "aynı hata kodu";
    }

    private static String typeLabel(TransactionType type) {
        return switch (type) {
            case Transfer -> "Havale";
            case Fast -> "FAST";
            case BulkPayment -> "Toplu Ödeme";
            case CorporateOrder -> "Kurumsal Talimat";
        };
    }
}
