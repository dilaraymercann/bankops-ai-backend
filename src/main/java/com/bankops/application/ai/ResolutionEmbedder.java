package com.bankops.application.ai;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Doğrulanmış çözümlerin embedding'ini hesaplayıp {@code resolutions.embedding} sütununa yazar. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResolutionEmbedder {

    private static final int BACKFILL_BATCH = 50;

    private final TextEmbedder embedder;
    private final JdbcTemplate jdbc;
    private final PiiMasker masker;

    /** Onaylanan tek bir çözümü embed eder. Bilgi tabanında olmayan çözüm için bir şey yapmaz. */
    public void embed(Long resolutionId) {
        List<Pending> pending = jdbc.query(pendingSql("AND r.id = ?"),
                (row, index) -> new Pending(row.getLong("id"), documentText(row.getString("error_description"), row.getString("text"))),
                resolutionId);
        store(pending);
    }

    /** Bilgi tabanında olup embedding'i eksik tüm çözümleri doldurur; yazılan satır sayısını döner. */
    public int backfillMissing() {
        int total = 0;
        while (true) {
            List<Pending> pending = jdbc.query(pendingSql("LIMIT " + BACKFILL_BATCH),
                    (row, index) -> new Pending(row.getLong("id"), documentText(row.getString("error_description"), row.getString("text"))));
            if (pending.isEmpty()) {
                return total;
            }
            store(pending);
            total += pending.size();
            log.info("Embedding yazıldı: {} çözüm", total);
        }
    }

    private void store(List<Pending> pending) {
        if (pending.isEmpty()) {
            return;
        }
        List<float[]> vectors = embedder.embedDocuments(pending.stream().map(Pending::text).toList());
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            rows.add(new Object[] {VectorLiteral.of(vectors.get(i)), pending.get(i).id()});
        }
        jdbc.batchUpdate("UPDATE resolutions SET embedding = ?::vector WHERE id = ?", rows);
    }

    private static String pendingSql(String suffix) {
        return """
                SELECT r.id, r.text, c.error_description
                FROM resolutions r
                JOIN exception_cases c ON c.id = r.case_id
                WHERE r.is_verified = TRUE AND r.include_in_knowledge_base = TRUE AND r.embedding IS NULL
                """ + suffix;
    }

    /**
     * Belge metni hata açıklamasını da içerir: arama sorgusu "hata açıklaması + işlem açıklaması" olduğundan
     * ikisi aynı konuşma alanında kalır (yalnızca çözüm metni embed edilseydi sorguya daha az benzerdi).
     */
    private String documentText(String errorDescription, String resolutionText) {
        return masker.mask("Hata: " + errorDescription + "\nÇözüm: " + resolutionText).text();
    }

    private record Pending(Long id, String text) {
    }
}
