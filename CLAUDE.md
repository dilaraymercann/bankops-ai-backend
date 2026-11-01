# BankOps AI — Proje Rehberi

Bu dosya, projeyi geliştiren yapay zekâ asistanı (Claude Code) ve geliştirici için tek kaynak (single source of truth) olarak hazırlanmıştır. Kod yazmadan önce bu dosyanın tamamını oku ve buradaki kararlara uy. Bir karar değişirse önce bu dosyayı güncelle.

> **Not:** Proje iki ayrı Git deposundan oluşur: `bankops_backend` ([bankops-ai-backend](https://github.com/dilaraymercann/bankops-ai-backend)) ve `bankops_frontend` ([bankops-ai-frontend](https://github.com/dilaraymercann/bankops-ai-frontend)). Depolar **public**'tir; repoya asla gizli bilgi (şifre, API anahtarı, `.env`) eklenmez. Bu dosyanın birebir aynı bir kopyası her iki deponun kökünde bulunur. Bir değişiklik yapıldığında **iki kopya da** güncellenmelidir.

---

## 1. Proje Özeti

**BankOps AI**, banka genel müdürlüklerindeki ödeme operasyonları ekiplerinin günlük iş süreçlerini kolaylaştırmak için geliştirilen, yapay zekâ destekli bir **operasyonel istisna yönetimi ve karar destek platformudur**.

Bankalarda her gün çok sayıda ödeme talimatı işlenir. Bazıları teknik sorunlar, eksik bilgi, doğrulama hataları, olası mükerrer kayıtlar veya gecikmeler nedeniyle manuel incelemeye düşer. Operasyon uzmanları bu kayıtları inceler, nedenini araştırır, geçmişte benzer bir sorun yaşanıp yaşanmadığını kontrol eder ve aksiyon alır.

BankOps AI bu süreci şu yollarla hızlandırır:

- İstisna kayıtlarını SLA ve önceliğe göre yönetilen bir iş kuyruğunda toplar.
- Geçmişte **doğrulanmış** çözümlerden yararlanarak açıklanabilir AI önerileri sunar.
- Dört göz prensibiyle çözümlerin onaylanmasını sağlar.
- Her onaylanan çözümü bilgi tabanına ekleyerek sistemin zamanla daha iyi öneri vermesini sağlar.
- Yöneticilere operasyon performansını ve AI'ın etkisini ölçen bir dashboard sunar.

### Kapsam dışı (kesin kurallar)

- Uygulama **gerçek bankacılık işlemi yapmaz**, para transferi gerçekleştirmez.
- AI **kesin karar vermez** ve kayıtlar üzerinde **otomatik aksiyon almaz**. Yalnızca öneri sunar.
- Tüm veriler **sentetiktir**. Gerçek müşteri, banka veya kurum verisi kullanılmaz.
- Uygulamada gerçek bir bankanın adı, logosu veya kurumsal kimliği kullanılmaz.

---

## 2. Geliştirici ile Çalışma Kuralları (Claude Code için)

Bu proje bir iş mülakatında sunulacak. Geliştirici, kodun her parçasını mülakatta açıklayabilmelidir. Bu nedenle:

1. **Fazlar halinde ilerle** (bkz. Bölüm 14). Bir faz bitmeden diğerine geçme.
2. Her fazın sonunda **dur** ve geliştiriciye Türkçe olarak kısa bir özet ver: ne yapıldı, hangi dosyalar eklendi, hangi kararlar neden alındı.
3. Önemli bir mimari karar verirken (yeni kütüphane, yeni katman, şema değişikliği) önce gerekçesini açıkla.
4. Basit ve okunabilir kodu, akıllıca ama karmaşık koda tercih et.
5. Bu dosyada yazmayan bir kütüphane eklemeden önce geliştiriciye sor.
6. Her fazın sonunda testlerin geçtiğinden ve uygulamanın çalıştığından emin ol.
7. **Commit ve push öncesi geliştiriciden onay al.** Hangi depoya hangi commit mesajıyla neyin gideceğini göster, onay gelmeden GitHub'a push etme (her push CI'ı tetikler ve depolar public'tir).

### Dil kuralları

- **Kod**: İngilizce (sınıf, metot, değişken, tablo, endpoint adları).
- **Kullanıcı arayüzü metinleri**: Türkçe.
- **Kod yorumları**: Türkçe veya İngilizce olabilir, ama bir dosya içinde tutarlı olmalı.
- **Commit mesajları**: İngilizce, kısa ve açıklayıcı (örn. `Add SLA background service`).

---

## 3. Teknoloji Yığını

| Katman | Teknoloji |
|---|---|
| Dil / Platform | Java 25 (LTS) |
| Backend | Spring Boot 4.1.x (Spring Initializr'daki güncel kararlı sürüm), Spring Web |
| Build aracı | Maven |
| Veritabanı | PostgreSQL + pgvector eklentisi |
| Veri erişimi | Spring Data JPA (Hibernate) + `hibernate-vector`; benzerlik araması için native SQL |
| Migration | Flyway |
| AI soyutlaması | Spring AI (`ChatClient`, `EmbeddingModel`) |
| LLM sağlayıcısı | Konfigürasyonla seçilir: bulut API sağlayıcısı **veya** Ollama (lokal) |
| Frontend | React + TypeScript + Vite |
| UI | shadcn/ui (Tailwind CSS), TanStack Table, TanStack Query, Recharts, React Router |
| Formlar | react-hook-form + zod |
| Kimlik doğrulama | Spring Security + JWT (OAuth2 Resource Server) + rol bazlı yetkilendirme |
| Altyapı | Docker Compose (lokal), GitHub Actions (build + test) |
| Yayın | Vercel (frontend), Render (backend), Supabase (PostgreSQL + pgvector) — bkz. Bölüm 20 |

### Backend yardımcı kütüphaneleri

- **Datafaker**: Sentetik veri üretimi (`tr` yerel ayarı)
- **Jakarta Bean Validation**: İstek doğrulama (`@Valid`, `@NotBlank` vb.)
- **SLF4J + Logback**: Loglama (Spring Boot varsayılanı)
- **springdoc-openapi**: API dokümantasyonu ve Swagger UI test ekranı
- **Lombok**: JPA entity'lerinde ve servislerde tekrar eden kodu azaltmak için kullanılır (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`). Entity'lerde `@Data` ve `@EqualsAndHashCode` **kullanılmaz** (JPA lazy ilişkilerinde sorun çıkarır). DTO'lar Lombok değil, Java `record` olarak yazılır.
- **JUnit 5 + AssertJ + Mockito + Testcontainers**: Birim ve entegrasyon testleri
- **Spring WebSocket (STOMP)** veya **Server-Sent Events**: (Opsiyonel, son faz) canlı kuyruk güncellemesi

### Bilinçli olarak kullanılmayanlar

Mikroservis, Kubernetes, Kafka, Redis, Spring Statemachine. Tek kişilik bir prototipte gereksiz karmaşıklık yaratırlar. Durum makinesi kütüphane yerine Domain katmanında sade bir Java sınıfı olarak yazılır (bkz. Bölüm 7); hem daha kolay okunur hem mülakatta açıklaması daha kolaydır. Proje **modüler monolit** olarak kurulur; AI analizi ve SLA motoru arayüzler arkasında ayrıldığı için ileride ayrı servislere bölünebilir.

---

## 4. Mimari ve Klasör Yapısı

Clean Architecture prensibiyle katmanlı bir modüler monolit. Tek bir Spring Boot uygulaması, katmanlar **paketlerle** ayrılır. Bağımlılık yönü içe doğrudur: `api → application → domain`, `infrastructure → application → domain`. **`domain` paketi Spring'e ve diğer paketlere bağımlı değildir** (JPA anotasyonları hariç).

Backend ve frontend **iki ayrı Git deposudur**; her birinin kendi CI'ı vardır.

```
BankOps/                              # Lokal çalışma klasörü (depo değil)
├── bankops_backend/                  # Backend deposu
│   ├── CLAUDE.md
│   ├── README.md
│   ├── docker-compose.yml            # PostgreSQL + pgvector
│   ├── .github/workflows/ci.yml      # mvn verify
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/bankops/
│       │   │   ├── BankOpsApplication.java
│       │   │   ├── domain/           # Entity'ler, enum'lar, durum makinesi, iş kuralları,
│       │   │   │                     # SlaCalculator, IbanValidator
│       │   │   ├── application/      # Use case servisleri, DTO'lar (record), arayüzler
│       │   │   │                     # (LlmAnalysisService, PiiMasker, SimilaritySearch)
│       │   │   ├── infrastructure/   # JPA repository'leri, pgvector sorguları,
│       │   │   │                     # Spring AI implementasyonları, maskeleme, güvenlik
│       │   │   ├── api/              # @RestController'lar, exception handler, SLA @Scheduled job
│       │   │   └── seed/             # Datafaker ile sentetik veri üretici ("seed" profili)
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/     # Flyway SQL dosyaları (V1__init.sql ...)
│       └── test/java/com/bankops/
│           ├── domain/               # Durum makinesi, SLA hesabı, maskeleme, IBAN doğrulama
│           └── api/                  # Testcontainers ile gerçek PostgreSQL üzerinde testler
└── bankops_frontend/                 # Frontend deposu (React + TypeScript + Vite)
    ├── CLAUDE.md
    ├── README.md
    ├── .github/workflows/ci.yml      # npm ci + build
    ├── docs/design/                  # Ekran tasarım taslakları (PDF)
    └── src/
        ├── api/                   # API istemcisi ve TanStack Query hook'ları
        ├── components/            # Ortak bileşenler (SlaBadge, PriorityBadge, StatusChip...)
        ├── features/
        │   ├── queue/             # İşlem kuyruğu
        │   ├── exception-detail/  # Kayıt detayı + AI analizi
        │   ├── approvals/         # Takım lideri onay ekranı
        │   ├── knowledge-base/    # Bilgi tabanı
        │   └── dashboard/         # Yönetici dashboard'u
        ├── auth/
        └── lib/                   # Formatlama (para, tarih), yardımcılar
```

### Temel mimari kurallar

- Para tutarları her yerde **`BigDecimal`** (Java) ve **`numeric(18,2)`** (PostgreSQL). `float`/`double` **asla** kullanılmaz. JSON'da tutarlar string olarak taşınır ve frontend'de `Intl.NumberFormat('tr-TR')` ile gösterilir.
- Zaman damgaları **UTC** olarak `Instant` tipinde saklanır, arayüzde `Europe/Istanbul` saat dilimiyle gösterilir.
- Zaman ihtiyacı olan her kod enjekte edilen `java.time.Clock` bean'ini kullanır (`Instant.now()` argümansız çağrılmaz). Testlerde `Clock.fixed(...)` verilerek SLA hesapları test edilebilir.
- Bağımlılıklar **constructor injection** ile alınır (`@Autowired` alan enjeksiyonu kullanılmaz).
- Controller'lar ince tutulur; iş mantığı application ve domain paketlerindedir.
- JPA entity'leri API'den doğrudan dönmez; her zaman DTO (`record`) kullanılır.
- Her durum değişikliği ve kritik işlem **audit log**'a yazılır.

---

## 5. Roller ve Yetkiler

| Rol | Kod | Yetkiler |
|---|---|---|
| Operasyon Uzmanı | `Specialist` | Kendine atanmış kayıtları görür, not ekler, durum değiştirir, AI analizi çalıştırır, çözüm önerir |
| Takım Lideri | `TeamLead` | Ekip kuyruğunu görür, kayıt atar, çözümleri onaylar veya revizyon ister, eskalasyonları görür |
| Yönetici | `Manager` | Dashboard ve raporları görür (salt okunur) |

### Dört göz prensibi (maker-checker)

- Bir çözümü **öneren kullanıcı, aynı çözümü onaylayamaz**. Bu kural Domain katmanında uygulanır ve testle doğrulanır.
- Bir kayıt yalnızca `TeamLead` rolündeki, çözümü önermemiş bir kullanıcı tarafından `Closed` durumuna geçirilebilir.

### Demo kullanıcıları (seed)

| Kullanıcı | Rol |
|---|---|
| Elif Kaya | Specialist |
| Can Arslan | Specialist |
| Zeynep Koç | Specialist |
| Mert Şahin | Specialist |
| Ayşe Yıldız | Specialist |
| Burak Demir | TeamLead |
| Selin Aydın | Manager |

Tüm demo kullanıcıları için şifre seed sırasında üretilir ve `README.md`'de "yalnızca lokal demo" notuyla yazılır.

---

## 6. Domain Modeli

### Ana entity'ler

**PaymentTransaction** — İstisnaya düşen ödeme talimatı (sentetik)
- `Id`, `Reference` (örn. `TRX-2026-000123`)
- `Type`: `Transfer` (Havale), `Fast`, `BulkPayment` (Toplu Ödeme), `CorporateOrder` (Kurumsal Talimat)
- `Amount` (BigDecimal), `Currency` (varsayılan `TRY`)
- `SenderName`, `SenderIban`, `ReceiverName`, `ReceiverIban`
- `Description`, `Channel` (İnternet Şubesi, Mobil, Şube, Kurumsal API)
- `ValueDate`, `CreatedAt`

**ExceptionCase** — Kuyruktaki istisna kaydı
- `Id`, `CaseNumber` (örn. `EXC-2026-10482`)
- `TransactionId`
- `ErrorCode` (bkz. Bölüm 8), `ErrorDescription`
- `Priority`: `Low`, `Medium`, `High`, `Critical`
- `Status` (bkz. Bölüm 7)
- `AssignedToUserId`, `ProposedByUserId`, `ClosedByUserId`
- `SlaDeadline`, `IsEscalated`
- `CreatedAt`, `UpdatedAt`, `ClosedAt`

**CaseNote** — `CaseId`, `AuthorUserId`, `Text`, `CreatedAt`

**StatusHistory** — `CaseId`, `FromStatus`, `ToStatus`, `ChangedByUserId`, `Reason`, `ChangedAt`

**Resolution** — Çözüm kaydı ve bilgi tabanı girdisi
- `CaseId`, `Text`, `ProposedByUserId`, `ApprovedByUserId`
- `IsVerified` (yalnızca onaylananlar `true`)
- `IncludeInKnowledgeBase` (onaylayan karar verir)
- `Embedding` (pgvector, `vector` tipi)
- `CreatedAt`, `ApprovedAt`

**AiAnalysis** — AI analiz sonucu (önbellek + geri bildirim)
- `CaseId`, `ResultJson`, `ReferencedCaseIds`, `MaskedFieldCount`
- `ModelName`, `LatencyMs`
- `Feedback`: `null`, `Useful`, `NotUseful`
- `CreatedAt`

**AuditLog** — `Timestamp`, `UserId`, `Action` (örn. `STATUS_CHANGE`, `AI_ANALYZE`, `AI_FEEDBACK`, `ESCALATE`, `APPROVE`), `EntityType`, `EntityId`, `Details` (JSON). **Sadece ekleme yapılır; güncelleme ve silme yoktur.**

**SlaPolicy** — `Priority`, `ErrorCode` (opsiyonel, daha spesifik kural), `DurationMinutes`

**User** — `Id`, `FullName`, `Username`, `Role`, `PasswordHash`

---

## 7. Durum Makinesi

```
New ──► Assigned ──► InReview ──► SolutionProposed ──► Closed
                       │  ▲               │
                       ▼  │               ▼
                  AwaitingInfo      (Revizyon) ──► InReview

Herhangi bir açık durumdan ──► Escalated (SLA aşımı veya manuel)
Escalated ──► InReview (takım lideri yeniden atadığında)
InReview ──► Rejected (talimat iade edildiğinde, onaya tabi değildir)
```

| Durum | Arayüz etiketi |
|---|---|
| `New` | Yeni |
| `Assigned` | Atandı |
| `InReview` | İncelemede |
| `AwaitingInfo` | Bilgi Bekleniyor |
| `SolutionProposed` | Çözüm Önerildi |
| `Escalated` | Eskale Edildi |
| `Closed` | Kapatıldı |
| `Rejected` | Reddedildi |

### Kurallar

- Geçişler domain paketinde sade bir `CaseStateMachine` sınıfıyla tanımlanır: izin verilen geçişler bir `Map<CaseStatus, Set<CaseStatus>>` içinde tutulur. Tanımlı olmayan bir geçiş denenirse domain `InvalidTransitionException` fırlatır ve API `409 Conflict` döner.
- `SolutionProposed → Closed` geçişi yalnızca `TeamLead` tarafından ve **dört göz kuralı** sağlanırsa yapılabilir.
- `SolutionProposed → InReview` (revizyon) geçişinde onaylayanın notu zorunludur.
- Her geçiş `StatusHistory` ve `AuditLog`'a yazılır.

---

## 8. Hata Kataloğu

Seed verisi bu kataloga göre üretilir.

| Kod | Açıklama (TR) | İşlem tipleri | Varsayılan öncelik |
|---|---|---|---|
| `IBAN_CHECKSUM` | IBAN kontrol hanesi hatalı | Havale, FAST | Medium |
| `NAME_MISMATCH` | Alıcı adı ile IBAN sahibi uyumsuz | Havale, FAST | High |
| `DUPLICATE_SUSPECT` | Mükerrer talimat şüphesi | Tümü | High |
| `FAST_LIMIT` | FAST tutar limiti aşımı | FAST | Medium |
| `OUT_OF_HOURS` | EFT saatleri dışında gelen talimat | Kurumsal Talimat | Low |
| `HOLIDAY_VALUE_DATE` | Valör tarihi resmi tatile denk geliyor | Kurumsal, Toplu | Medium |
| `ACCOUNT_BLOCKED` | Alıcı hesap bloke/kapalı | Tümü | Medium |
| `SANCTION_HIT` | Yaptırım listesi isim benzerliği | Tümü | Critical |
| `BATCH_FORMAT` | Toplu ödeme dosyasında satır format hatası | Toplu Ödeme | Medium |
| `TIMEOUT` | Karşı bankadan yanıt alınamadı, durum belirsiz | FAST | Critical |

---

## 9. SLA Kuralları

### Varsayılan süreler

| Öncelik | SLA |
|---|---|
| Critical | 30 dakika |
| High | 2 saat |
| Medium | 8 saat |
| Low | 2 iş günü |

`SlaPolicy` tablosunda hata koduna özel daha spesifik bir kural varsa o geçerlidir.

### Hesaplama

- SLA **mesai saatlerine** göre işler: hafta içi 08:30–17:30 (Europe/Istanbul). Hafta sonu ve seed'de tanımlı resmi tatiller sayılmaz.
- `Critical` öncelikli kayıtlar istisnadır: **7/24** işler (FAST da 7/24 çalıştığı için).
- Hesaplama mantığı domain paketindeki `SlaCalculator` sınıfındadır ve kapsamlı birim testine sahiptir (mesai bitimi, hafta sonu, tatil geçişleri).

### Görsel durumlar

| Durum | Koşul | Renk |
|---|---|---|
| Normal | Sürenin %80'inden azı doldu | Nötr gri |
| Yaklaşıyor | %80 veya fazlası doldu | Amber |
| Aşıldı | Süre doldu | Kırmızı |

### Otomatik eskalasyon

- `@Scheduled(fixedDelayString = "${bankops.sla.check-interval}")` ile çalışan bir job her dakika açık kayıtları kontrol eder (`@EnableScheduling` gerekir).
- SLA'sı aşılan ve henüz eskale edilmemiş kayıtlar `IsEscalated = true` olarak işaretlenir, takım liderine bildirim düşer ve `AuditLog`'a `ESCALATE` kaydı yazılır.
- Eskalasyon kaydın durumunu değiştirmez, yalnızca işaretler (uzman çalışmaya devam edebilir).

---

## 10. AI Analiz Akışı (RAG)

Kullanıcı kayıt detayında **"AI ile Analiz Et"** butonuna bastığında:

### 1. Önbellek kontrolü
Aynı kayıt için son 24 saatte üretilmiş bir analiz varsa ve kayıt o zamandan beri değişmediyse, mevcut analiz döner. "Yeniden Analiz Et" butonu önbelleği atlar.

### 2. Hibrit benzerlik araması (`SimilaritySearch`)
- **Yapısal filtre**: Aynı `ErrorCode` ve tercihen aynı işlem tipi.
- **Semantik sıralama**: Kaydın hata açıklaması + işlem açıklamasının embedding'i ile `Resolution.Embedding` arasında kosinüs benzerliği (pgvector `<=>` operatörü). Bu sorgu okunabilirlik için **native SQL** olarak yazılır (`JdbcTemplate` veya `@Query(nativeQuery = true)`).
- **Sadece** `IsVerified = true` ve `IncludeInKnowledgeBase = true` olan çözümler aranır.
- En benzer **en fazla 5** kayıt, minimum benzerlik eşiği **0.70** ile döner.

### 3. Fallback
Eşiği geçen kayıt yoksa LLM çağrılmaz. Arayüzde şu mesaj gösterilir: *"Güvenilir geçmiş kayıt bulunamadı. Bu kayıt için AI önerisi üretilmedi."* Bu kasıtlı bir tasarım kararıdır: AI dayanaksız tahmin yürütmez.

### 4. Kişisel veri maskeleme (`PiiMasker`)
LLM'e gönderilen **her** metin önce maskelenir:
- TCKN (11 hane) → `[TCKN]`
- IBAN (`TR` + 24 hane, boşluklu/boşluksuz) → `[IBAN]`
- Gönderen/alıcı adları → `[GÖNDEREN]`, `[ALICI]`
- Telefon numaraları → `[TELEFON]`

Maskelenen alan sayısı `AiAnalysis.MaskedFieldCount`'a yazılır ve arayüzde gösterilir. Maskeleme servisi kapsamlı birim testlerine sahiptir.

### 5. LLM çağrısı
- Spring AI `ChatClient` üzerinden yapılır. Sağlayıcı `application.yml` ve Spring profilleriyle seçilir (örn. `ollama` veya `cloud` profili). Uygulama kodu belirli bir sağlayıcıya bağımlı değildir.
- Yanıt, Spring AI'ın yapılandırılmış çıktı desteğiyle (`.entity(AiAnalysisResult.class)`) bir Java `record`'una dönüştürülür.
- Sistem promptu Türkçedir ve şunları içerir: AI'ın sadece öneri sunduğu, kesin karar vermediği, yalnızca verilen geçmiş kayıtlara dayanması gerektiği, kanıt yoksa bunu açıkça söylemesi gerektiği.
- LLM'den **yapılandırılmış JSON** istenir:

```json
{
  "summary": "Problemin anlaşılır açıklaması",
  "possibleCauses": [{ "cause": "...", "supportingCaseIds": ["EXC-2026-08817"] }],
  "recommendedSteps": ["..."],
  "warnings": ["..."],
  "referencedCaseIds": ["EXC-2026-08817", "EXC-2026-07402"]
}
```

### 6. Doğrulama (halüsinasyon kontrolü)
- JSON şemaya uymuyorsa bir kez yeniden denenir, yine uymuyorsa hata döner.
- `referencedCaseIds` içindeki **her** ID'nin 2. adımda gerçekten getirilen kayıtlar arasında olduğu doğrulanır. Olmayan ID'ler yanıttan çıkarılır ve loglanır.

### 7. Kayıt ve gösterim
Sonuç `AiAnalysis` tablosuna yazılır, `AuditLog`'a `AI_ANALYZE` kaydı düşer. Arayüzde her referans kaydın yanında **benzerlik skoru** ve **eşleşme nedeni** (örn. "aynı hata kodu · FAST") gösterilir.

### 8. Geri bildirim
Uzman analizi **Faydalı / Faydalı değil** olarak işaretler. Bu veri dashboard'daki AI kabul oranını besler.

### 9. Bilgi tabanının büyümesi
Bir çözüm takım lideri tarafından onaylanıp `IncludeInKnowledgeBase = true` seçildiğinde, çözüm metninin embedding'i hesaplanır ve bilgi tabanına eklenir. **Onaylanmamış çözümler asla AI kaynağı olarak kullanılmaz.**

---

## 11. API Uç Noktaları

Tüm uç noktalar `/api` öneki ile başlar ve JWT gerektirir (login hariç).

### Kimlik doğrulama
- `POST /api/auth/login` → JWT döner
- `GET /api/auth/me` → aktif kullanıcı ve rolü

### İstisna kayıtları
- `GET /api/cases` → filtreli liste (`status`, `priority`, `errorCode`, `transactionType`, `slaState`, `assignedTo`, `search`, `page`, `pageSize`, `sort`). Uzman yalnızca kendine atananları görür; takım lideri tümünü görür.
- `GET /api/cases/summary` → kuyruk özet kartları için sayılar
- `GET /api/cases/{id}` → detay (işlem bilgileri, notlar, durum geçmişi)
- `POST /api/cases/{id}/notes` → not ekle
- `POST /api/cases/{id}/transitions` → durum değiştir (`{ "toStatus": "...", "reason": "..." }`)
- `POST /api/cases/{id}/resolution` → çözüm öner (durumu `SolutionProposed` yapar)
- `POST /api/cases/{id}/assign` → kayıt ata (TeamLead)

### AI
- `POST /api/cases/{id}/ai-analysis` → analiz üret veya önbellekten getir (`?refresh=true` önbelleği atlar)
- `POST /api/ai-analyses/{id}/feedback` → `{ "feedback": "Useful" | "NotUseful" }`

### Onaylar (TeamLead)
- `GET /api/approvals` → onay bekleyen çözümler
- `POST /api/approvals/{caseId}/approve` → `{ "includeInKnowledgeBase": true, "note": "..." }`
- `POST /api/approvals/{caseId}/request-revision` → `{ "note": "..." }` (not zorunlu)

### Bilgi tabanı
- `GET /api/knowledge-base` → doğrulanmış çözümler (arama + hata kodu filtresi)

### Dashboard (Manager, TeamLead)
- `GET /api/dashboard/kpis`
- `GET /api/dashboard/ai-impact`
- `GET /api/dashboard/error-types`
- `GET /api/dashboard/sla-trend`
- `GET /api/dashboard/aging`
- `GET /api/dashboard/transaction-types`
- `GET /api/dashboard/ai-quality`
- `GET /api/dashboard/team`
- `GET /api/dashboard/hourly-heatmap`

### Hata formatı
Tüm hatalar Spring'in yerleşik **`ProblemDetail`** desteğiyle (RFC 9457) bir `@RestControllerAdvice` üzerinden döner. Geçersiz durum geçişi `409`, yetki hatası `403`, doğrulama hatası `400`.

---

## 12. Ekranlar

Görsel tasarım taslağı `bankops_frontend/docs/design/BankOps AI Ekranları.pdf` dosyasındadır (İşlem Kuyruğu, Kayıt Detayı, Onay, Dashboard). Arayüz bu taslağa sadık kalınarak geliştirilir. Genel görünüm: sol tarafta koyu lacivert (`#0F1B2A`) menü, açık gri (`#F4F6F8`) zemin, beyaz kartlar, vurgu rengi `#1D5C8F`, yazı tipi **IBM Plex Sans** (kodlar ve tutarlar için **IBM Plex Mono**). Uygulamanın her ekranında "Sentetik demo verisi" etiketi bulunur.

### Renk anlamları
| Anlam | Metin | Arka plan |
|---|---|---|
| Kritik / SLA aşıldı | `#A3241B` | `#FCEBEA` |
| Yüksek / SLA yaklaşıyor | `#7A5200` | `#FFF3D6` |
| Orta | `#1D5C8F` | `#E6F0F8` |
| Düşük / nötr | `#3E4957` | `#EEF1F4` |
| Başarılı / onaylı | `#1E6B3A` | `#E6F4EA` |

### 12.1 İşlem Kuyruğu (Specialist, TeamLead)
- Özet kartları: SLA aşıldı, SLA yaklaşıyor, Yeni, İncelemede
- Filtreler: arama, öncelik, hata türü, işlem tipi, SLA durumu
- Tablo sütunları: Kayıt No, İşlem Tipi, Hata (kod + açıklama), Tutar, Öncelik, Durum, SLA (kalan süre + ilerleme çubuğu), Oluşturma
- Varsayılan sıralama: SLA'ya en yakın olan en üstte
- SLA'sı aşılan satırlar hafif kırmızı zeminle vurgulanır
- Sayfalama

### 12.2 Kayıt Detayı ve AI Analizi
- Üst kısım: kayıt numarası, öncelik, durum, SLA uyarı bandı
- **Sol kolon**: İşlem bilgileri (IBAN ve isimler **maskeli** gösterilir), hata açıklaması, işlem geçmişi (zaman çizelgesi), aksiyon formu (yeni durum, çözüm açıklaması, "Not Ekle", "Onaya Gönder")
- Aksiyon formunun altında dört göz bilgilendirmesi: *"Bu kaydı siz kapatamazsınız, çözüm takım liderinin onayına gider."*
- **Sağ kolon**: AI Analizi paneli
  - Başlıkta "Öneri · Karar vermez" etiketi ve "Yeniden Analiz Et" butonu
  - Bilgi çipleri: kaç benzer kayıt kullanıldı, kaç alan maskelendi, analiz zamanı
  - Bölümler: Problemin açıklaması, Olası nedenler, Önerilen inceleme adımları, Dikkat edilmesi gerekenler (amber kutu), Dayandığı geçmiş kayıtlar (benzerlik skoru + eşleşme nedeni + çözüm özeti)
  - Geri bildirim: "Faydalı" / "Faydalı değil"
  - Analiz yüklenirken iskelet (skeleton) gösterimi; fallback durumunda açıklayıcı boş durum mesajı

### 12.3 Takım Lideri Onayı (TeamLead)
- Sol: onay bekleyen çözümler listesi
- Sağ: seçili çözümün incelemesi
  - Dört göz kontrol sonucu (geçti / geçmedi)
  - Önerilen çözüm metni
  - Özet kutuları: AI önerisiyle uyum, uzman geri bildirimi, toplam çözüm süresi
  - Doğrulama kontrol listesi
  - "Bilgi tabanına güvenilir çözüm olarak ekle" onay kutusu (varsayılan işaretli)
  - Onay notu alanı
  - "Onayla ve Kapat" / "Revizyon İste" butonları
  - Kaydın son audit log satırları

### 12.4 Bilgi Tabanı
- Doğrulanmış çözümlerin aranabilir listesi (hata kodu filtresi, metin araması)
- Her kayıtta: çözüm metni, öneren, onaylayan, onay tarihi, kaç kez AI referansı olarak kullanıldığı

### 12.5 Yönetici Dashboard (Manager, TeamLead)

**KPI kartları (zorunlu, tek sıra halinde):**
1. Toplam operasyon kaydı (seçili dönem)
2. Açık istisna sayısı
3. İnceleme bekleyen kayıt sayısı
4. Yüksek öncelikli kayıt sayısı (Critical + High)
5. SLA süresi aşılmış kayıt sayısı
6. Ortalama çözüm süresi

**Paneller:**
- **AI etkisi**: AI analizi kullanılan ve kullanılmayan kayıtların ortalama çözüm süresi karşılaştırması + AI öneri kabul oranı
- **En sık hata türleri**: yatay çubuk grafik
- **Günlük SLA ihlal oranı**: son 14 iş günü, %8 hedef çizgisiyle
- **Yaşlanan açık kayıtlar**: 0–1 saat, 1–4 saat, 4–8 saat, 1–3 gün, 3 gün+
- **İşlem tipine göre istisnalar**: yığılmış çubuk + lejant
- **Hata türüne göre AI başarısı ve bilgi tabanı**: tablo (analiz sayısı, kabul oranı, doğrulanmış çözüm sayısı, durum etiketi: Güçlü ≥%75 / Gelişiyor %50–74 / Bilgi tabanı zayıf <%50)
- **Ekip iş yükü ve performans**: uzman bazında açık kayıt, bugün kapanan, ortalama süre, SLA aşan, AI kullanım oranı
- **Saatlik istisna yoğunluğu**: hafta içi günler × 08:00–18:00 ısı haritası

Dönem seçici: Son 7 / 30 / 90 gün.

### Ortak arayüz kuralları
- Tüm ekranlar mobil genişlikte kullanılabilir olmalı (menü üste taşınır, tablolar yatay kaydırılır).
- Erişilebilirlik: gerçek `<button>`, `<label>` kullanımı, yeterli kontrast, renk tek başına anlam taşımaz (her renkli durumun yanında metin etiketi bulunur).
- Para formatı: `48.750,00 TL`. Tarih formatı: `01.11.2026 14:32`.

---

## 13. Sentetik Veri (Seeder)

`seed` paketi, **Datafaker** ile `tr` yerel ayarında veri üretir. Uygulama `seed` Spring profiliyle başlatıldığında bir `CommandLineRunner` veritabanını doldurur ve kapanır. Seed **deterministiktir** (sabit random seed), böylece her çalıştırmada aynı demo verisi oluşur.

### Üretilecek veri
- 7 demo kullanıcısı (Bölüm 5)
- Son 90 güne yayılmış **yaklaşık 2.500** istisna kaydı
  - Hata türü dağılımı gerçekçi olmalı (`NAME_MISMATCH` ve `DUPLICATE_SUSPECT` en sık, `SANCTION_HIT` en seyrek)
  - Saatlik dağılım: hafta içi 14:00–16:00 arası yoğun, Pazartesi ve Cuma daha yüksek
  - **Yaklaşık 140** açık kayıt (farklı durumlarda, bir kısmı SLA'yı aşmış)
- **300–500** doğrulanmış çözüm (bilgi tabanının çekirdeği)
  - Her hata türü için gerçekçi, çeşitli Türkçe çözüm metinleri (örn. *"Müşteri temsilcisi aracılığıyla doğru IBAN teyit edildi, talimat düzeltilerek yeniden gönderildi."*)
  - `SANCTION_HIT` için bilerek az sayıda (yaklaşık 10) çözüm üret; dashboard'daki "bilgi tabanı zayıf" senaryosu böylece gerçekçi görünür
- Geçmiş kayıtlar için `AiAnalysis` ve geri bildirim verileri (dashboard grafiklerinin dolu görünmesi için)
- IBAN'lar **geçerli kontrol hanesine sahip** ama kurgusal olmalı (mod-97 ile üretilir)
- Kurum adları tamamen kurgusal olmalı (gerçek şirket adı kullanılmaz)

### Embedding'ler
Seeder, doğrulanmış çözümlerin embedding'lerini toplu olarak hesaplar. AI sağlayıcısı yapılandırılmamışsa embedding adımı atlanır ve uyarı loglanır (uygulama AI özelliği olmadan da çalışabilmelidir).

---

## 14. Geliştirme Fazları

Her faz sonunda: testler geçmeli, uygulama çalışmalı, geliştiriciye özet verilmeli.

### Faz 0 — Proje iskeleti
- Spring Initializr ile Maven projesi (Web, Data JPA, Security, Validation, PostgreSQL Driver, Flyway, Actuator)
- Paket yapısı (domain, application, infrastructure, api, seed)
- `docker-compose.yml` (PostgreSQL + pgvector)
- İlk Flyway migration'ı (`V1__init.sql`, `CREATE EXTENSION vector`)
- springdoc-openapi, Actuator health endpoint'i
- React + Vite + TypeScript + Tailwind + shadcn/ui kurulumu
- GitHub Actions CI: backend deposunda `mvn verify`, frontend deposunda `npm ci && npm run build`
- **Kabul**: `docker compose up` ile veritabanı, `./mvnw spring-boot:run` ile API, `npm run dev` ile frontend ayağa kalkar.

### Faz 1 — Domain ve veri
- Tüm entity'ler, enum'lar, Flyway şeması, `CaseStateMachine`
- `Clock` bean'i, `SlaCalculator`, `IbanValidator` (mod-97)
- Datafaker ile seed
- **Kabul**: Durum makinesi, SLA hesabı ve IBAN doğrulama için birim testleri geçer. Seeder veritabanını doldurur.

### Faz 2 — Kimlik doğrulama ve kuyruk
- JWT login, rol bazlı policy'ler
- Kuyruk ve detay endpoint'leri
- Frontend: login, ana layout (sol menü), İşlem Kuyruğu, Kayıt Detayı (AI paneli hariç)
- **Kabul**: Uzman giriş yapıp kendi kuyruğunu filtreleyebilir ve detay görebilir.

### Faz 3 — İş akışı ve audit
- Not ekleme, durum geçişleri, çözüm önerme, atama
- Onay ekranı ve dört göz kuralı
- Audit log
- SLA `@Scheduled` job'u ve eskalasyon
- **Kabul**: Bir kayıt uzman tarafından çözülüp takım lideri tarafından kapatılabilir. Uzman kendi çözümünü onaylayamaz (testle doğrulanır). SLA aşımı otomatik eskalasyon üretir.

### Faz 4 — AI analizi
- `PiiMasker` (birim testleriyle)
- Embedding üretimi ve pgvector benzerlik araması
- `LlmAnalysisService`: prompt, JSON çıktısı, referans doğrulama, fallback, önbellek
- Geri bildirim
- Onaylanan çözümlerin bilgi tabanına eklenmesi
- Frontend: AI Analizi paneli, Bilgi Tabanı ekranı
- LLM sağlayıcı seçimi: canlı demo için bulut API'sı gerekir (bkz. Bölüm 20)
- **Kabul**: Analiz yalnızca doğrulanmış kayıtlara dayanır, kişisel veriler LLM'e gitmez (testle doğrulanır), dayanak yoksa fallback mesajı gösterilir.

### Faz 5 — Dashboard
- Dashboard endpoint'leri
- 6 KPI kartı ve tüm paneller (Recharts)
- **Kabul**: Tüm göstergeler seed verisinden doğru hesaplanır, dönem seçici çalışır.

### Faz 6 — Cilalama (zaman kalırsa)
- WebSocket (STOMP) veya Server-Sent Events ile canlı kuyruk güncellemesi
- Takım lideri için kayıt yeniden dağıtma
- Boş durumlar, yükleme durumları, hata mesajlarının gözden geçirilmesi
- README'nin tamamlanması (ekran görüntüleri, mimari diyagram, demo senaryosu, canlı demo linki)
- **Yayın** (bkz. Bölüm 20): backend `Dockerfile`, Render + Supabase + Vercel kurulumu, seed verisinin yüklenmesi, canlı ortamda demo senaryosunun denenmesi

---

## 15. Test Stratejisi

**Zorunlu birim testleri (`src/test/.../domain`, JUnit 5 + AssertJ):**
- Durum makinesi: geçerli ve geçersiz tüm geçişler
- Dört göz kuralı: öneren kişi onaylayamaz
- SLA hesabı: mesai içi, mesai dışı, hafta sonu, tatil, Critical için 7/24
- IBAN doğrulama: geçerli, kontrol hanesi hatalı, yanlış uzunluk
- PII maskeleme: TCKN, IBAN (boşluklu/boşluksuz), isim, telefon; maskelenmemesi gereken metinlerin bozulmaması
- AI referans doğrulama: getirilmemiş bir ID'nin yanıttan çıkarılması

**Entegrasyon testleri (`src/test/.../api`, `@SpringBootTest` + Testcontainers):**
- Yetki: uzman başkasının kaydını göremez, uzman onay endpoint'ine erişemez
- Uçtan uca akış: kayıt → çözüm önerisi → onay → bilgi tabanında görünme
- AI analizi için LLM sahte (mock) bir `ChatModel` ile test edilir; gerçek API çağrısı yapılmaz

---

## 16. Güvenlik ve Gizlilik

- Kişisel veriler (IBAN, isim) arayüzde varsayılan olarak **maskeli** gösterilir.
- LLM'e giden tüm metinler `PiiMasker`'dan geçer. Bu kural bir testle güvence altına alınır.
- API anahtarları ve bağlantı bilgileri **asla** koda veya repoya yazılmaz; ortam değişkenleri kullanılır (`application.yml` içinde `${OPENAI_API_KEY}` gibi). `.env` dosyaları `.gitignore`'dadır.
- Audit log yalnızca eklemeye açıktır.
- Parolalar Spring Security'nin `BCryptPasswordEncoder`'ı ile hash'lenir.
- CORS yalnızca frontend'in adresine izin verir.

---

## 17. Konfigürasyon

`application.yml` örneği (gizli değerler ortam değişkenlerinden okunur):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bankops
    username: bankops
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate      # Şemayı Flyway yönetir, Hibernate sadece doğrular
    open-in-view: false
  flyway:
    enabled: true
  ai:
    ollama:
      chat:
        options:
          model: <model-adi>
      embedding:
        options:
          model: <embedding-model-adi>

bankops:
  jwt:
    issuer: BankOps
    expiry-minutes: 480
    secret: ${JWT_SECRET}
  ai:
    similarity-threshold: 0.70
    max-references: 5
    cache-hours: 24
  sla:
    business-hours-start: "08:30"
    business-hours-end: "17:30"
    time-zone: Europe/Istanbul
    check-interval: 60000     # milisaniye
```

Uygulamaya özel ayarlar `bankops.*` altında tutulur ve `@ConfigurationProperties` ile tip güvenli `record`'lara bağlanır.

Embedding modeli **Türkçe destekleyen çok dilli** bir model olmalıdır.

---

## 18. Demo Senaryosu (mülakat için, ~3 dakika)

1. **Elif Kaya** (uzman) olarak giriş yap. Kuyrukta SLA'sı aşılmış `NAME_MISMATCH` kaydını göster.
2. Kaydı aç: maskeli işlem bilgileri, işlem geçmişi ve eskalasyon bandı.
3. **"AI ile Analiz Et"**: 3 benzer doğrulanmış kayda dayanan öneri, benzerlik skorları ve maskelenen alan sayısı.
4. "Faydalı" işaretle, çözüm açıklamasını yaz ve **onaya gönder**. Kaydı kapatamadığını göster (dört göz).
5. **Burak Demir** (takım lideri) olarak giriş yap, çözümü onayla ve bilgi tabanına ekle.
6. **Selin Aydın** (yönetici) olarak giriş yap. Dashboard'da KPI'ları, AI etkisini ve "bilgi tabanı zayıf" olan `SANCTION_HIT` satırını göster.

---

## 19. Komutlar

```bash
# Veritabanını başlat (bankops_backend klasöründe)
cd bankops_backend && docker compose up -d

# Backend: derle ve testleri çalıştır (Flyway migration'ları uygulama açılışında otomatik çalışır)
./mvnw verify

# Sentetik veri üret
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed

# API'yi çalıştır (Swagger UI: /swagger-ui.html)
./mvnw spring-boot:run

# Frontend
cd ../bankops_frontend && npm install && npm run dev
```

---

## 20. Yayın (Deploy)

Uygulama, mülakatçının bir linkten kendisi deneyebilmesi için bulutta yayınlanır.

| Parça | Platform | Notlar |
|---|---|---|
| Frontend | **Vercel** | `bankops-ai-frontend` deposuna bağlanır; `main`'e her push'ta otomatik yayın. React Router için tüm yollar `index.html`'e yönlendirilir (`vercel.json` rewrite). API adresi `VITE_API_BASE_URL` ortam değişkeninden okunur. |
| Backend | **Render** (Docker web service) | Depodaki `Dockerfile` ile derlenir. Tüm ayarlar Render'ın ortam değişkenlerinden gelir (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, LLM anahtarı). |
| Veritabanı | **Supabase** (PostgreSQL + pgvector) | Flyway migration'ları uygulama açılışında Supabase'e uygulanır. Bağlantı için **Session pooler** adresi kullanılır (Render IPv4 üzerinden bağlanır, doğrudan bağlantı IPv6 olabilir; transaction pooler Flyway ile uyumlu değildir). |

### Kurallar

- Lokal ile yayın arasındaki **tek fark ortam değişkenleridir**; koda ortama özel `if` yazılmaz. Lokalde `.env`, yayında platformun ortam değişkenleri kullanılır.
- CORS izinli adresleri ve frontend'in API adresi ayardan okunur, koda sabit yazılmaz.
- Canlı demoda AI için **bulut LLM API'sı** kullanılır (Ollama ücretsiz bulut katmanlarında çalıştırılamaz). Sağlayıcı seçimi Faz 4'te yapılır; lokal geliştirmede Ollama seçeneği korunabilir.
- Demo herkese açık olduğundan AI analiz uç noktasına basit bir istek sınırı (rate limit) konur; LLM API anahtarının harcama limiti sağlayıcı panelinden sınırlandırılır.
- Seed verisi Supabase'e bir kez, `seed` profiliyle yüklenir.
- Ücretsiz katman sınırlamaları (koşullar değişebilir, yayın öncesi kontrol edilir): Render ücretsiz servisi bir süre istek almazsa uykuya geçer ve ilk istekte yavaş açılır; Supabase ücretsiz projesi uzun süre kullanılmazsa duraklatılabilir. Mülakattan önce link açılıp "ısıtılmalı".
