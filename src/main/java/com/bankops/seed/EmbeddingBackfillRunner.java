package com.bankops.seed;

import com.bankops.application.ai.ResolutionEmbedder;
import com.bankops.application.ai.TextEmbedder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * "embed" profiliyle başlatıldığında bilgi tabanındaki embedding'i eksik çözümleri doldurur ve kapanır.
 * Seed'den sonra bir kez (ve Supabase'e yükleme sonrası) çalıştırılır; mevcut veriye dokunmaz.
 */
@Slf4j
@Component
@Profile("embed")
class EmbeddingBackfillRunner implements CommandLineRunner {

    private final ResolutionEmbedder embedder;
    private final TextEmbedder textEmbedder;
    private final ConfigurableApplicationContext context;

    EmbeddingBackfillRunner(ResolutionEmbedder embedder, TextEmbedder textEmbedder, ConfigurableApplicationContext context) {
        this.embedder = embedder;
        this.textEmbedder = textEmbedder;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        int exitCode = 0;
        if (!textEmbedder.isConfigured()) {
            log.warn("GEMINI_API_KEY tanımlı değil, embedding hesaplanmadı");
            exitCode = 1;
        } else {
            log.info("Embedding tamamlandı: {} çözüm güncellendi", embedder.backfillMissing());
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
