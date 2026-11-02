# BankOps AI — Backend

Banka ödeme operasyonları için yapay zekâ destekli istisna yönetimi ve karar destek platformunun backend'i.
Frontend: [bankops-ai-frontend](https://github.com/dilaraymercann/bankops-ai-frontend)

> Tüm veriler **sentetiktir**. Uygulama gerçek bankacılık işlemi yapmaz.

## Teknolojiler

Java 25 · Spring Boot 4.1 · PostgreSQL 18 + pgvector · Flyway · Spring Security · springdoc-openapi · Testcontainers

## Gereksinimler

- JDK 25
- Docker (PostgreSQL ve Testcontainers testleri için)

Maven kurmaya gerek yoktur; proje Maven Wrapper (`./mvnw`) ile gelir.

## Çalıştırma

```bash
# 1. Ortam değişkenlerini hazırla (yalnızca ilk sefer)
cp .env.example .env        # DB_PASSWORD ve JWT_SECRET değerlerini değiştir (JWT_SECRET: openssl rand -hex 32)

# 2. Veritabanını başlat
docker compose up -d

# 3. API'yi çalıştır
./mvnw spring-boot:run
```

- Health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html

## Giriş (JWT)

`POST /api/auth/login` ile `{ "username", "password" }` gönderilir, dönen `token` her istekte `Authorization: Bearer <token>` olarak kullanılır. CORS yalnızca `CORS_ALLOWED_ORIGINS` adresine izin verir (varsayılan: `http://localhost:5173`).

## Demo verisi (seed)

Sentetik veriyi (7 kullanıcı, ~2.500 istisna kaydı, ~450 bilgi tabanı çözümü) üretir ve kapanır:

```bash
# .env dosyasında SEED_DEMO_PASSWORD tanımlı olmalı (demo kullanıcılarının ortak parolası)
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
```

Kullanıcı adları: `elif.kaya`, `can.arslan`, `zeynep.koc`, `mert.sahin`, `ayse.yildiz` (uzman), `burak.demir` (takım lideri), `selin.aydin` (yönetici). Parola yalnızca **lokal demo** içindir ve repoda tutulmaz.

Veritabanı doluysa seed atlanır. Sıfırdan üretmek için: `docker compose down -v && docker compose up -d`.

## Test

```bash
./mvnw verify
```

Entegrasyon testleri Testcontainers ile geçici bir PostgreSQL (pgvector) container'ı başlatır; Docker'ın çalışıyor olması yeterlidir.

## Mimari

Clean Architecture prensibiyle katmanlı modüler monolit:

| Paket | Sorumluluk |
|---|---|
| `domain` | Entity'ler, durum makinesi, iş kuralları (Spring'e bağımlı değil) |
| `application` | Use case servisleri, DTO'lar, arayüzler |
| `infrastructure` | Veritabanı, AI sağlayıcısı, güvenlik implementasyonları |
| `api` | REST controller'lar, hata yönetimi, zamanlanmış görevler |
| `seed` | Sentetik demo verisi üretici |

Ayrıntılı proje rehberi: [CLAUDE.md](CLAUDE.md)
