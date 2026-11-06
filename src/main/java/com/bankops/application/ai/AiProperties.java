package com.bankops.application.ai;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code bankops.ai.*} ayarları (CLAUDE.md §17). Gizli değer (API anahtarı) yalnızca ortam değişkeninden gelir. */
@ConfigurationProperties("bankops.ai")
public record AiProperties(
        double similarityThreshold,
        int maxReferences,
        int cacheHours,
        int userLimitPerMinute,
        int globalLimitPerMinute,
        Gemini gemini) {

    /** Gemini API ayarları. {@code chatModels} sırayla denenir: ilki yoğunsa bir sonrakine geçilir. */
    public record Gemini(
            String apiKey,
            String baseUrl,
            List<String> chatModels,
            String embeddingModel,
            int embeddingDimensions) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
