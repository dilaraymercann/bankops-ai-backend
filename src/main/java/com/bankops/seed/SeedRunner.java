package com.bankops.seed;

import com.bankops.application.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** "seed" profiliyle başlatıldığında veritabanını doldurur ve uygulamayı kapatır (CLAUDE.md §13). */
@Slf4j
@Component
@Profile("seed")
class SeedRunner implements CommandLineRunner {

    private final SeedDataService seedDataService;
    private final UserRepository userRepository;
    private final ConfigurableApplicationContext context;
    private final String demoPassword;

    SeedRunner(SeedDataService seedDataService, UserRepository userRepository, ConfigurableApplicationContext context,
               @Value("${bankops.seed.demo-password}") String demoPassword) {
        this.seedDataService = seedDataService;
        this.userRepository = userRepository;
        this.context = context;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.warn("Veritabanı zaten dolu, seed atlandı. Sıfırdan üretmek için: docker compose down -v");
        } else {
            seedDataService.seed(demoPassword);
        }
        System.exit(SpringApplication.exit(context));
    }
}
