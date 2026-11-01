package com.bankops.seed;

import com.bankops.domain.ErrorCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Sentetik verinin Türkçe metinleri ve kurgusal kurum adı parçaları. Gerçek bir kurum adı içermez. */
final class SeedTexts {

    private SeedTexts() {
    }

    static final List<String> COMPANY_PREFIXES = List.of(
            "Anadolu", "Marmara", "Ege", "Karadeniz", "Akdeniz", "Trakya", "Toros", "Güneş", "Mavi", "Kuzey",
            "Doğu", "Zirve", "Atlas", "Yeşil", "Bereket", "Kristal", "Pusula", "Çınar");

    static final List<String> COMPANY_SECTORS = List.of(
            "Hasat Tarım", "Yıldız Tekstil", "Lider Lojistik", "Öncü İnşaat", "Vizyon Gıda", "Dinamik Otomotiv",
            "Merkez Ambalaj", "Başak Un", "Ufuk Yazılım", "Işık Enerji", "Doğal Kimya", "Güven Turizm",
            "Sağlam Çelik", "Rota Nakliyat", "Özgün Mobilya", "Derya Su Ürünleri");

    static final List<String> COMPANY_SUFFIXES = List.of("Ltd. Şti.", "A.Ş.", "San. ve Tic. A.Ş.", "Ltd. Şti.");

    static final List<String> PAYMENT_DESCRIPTIONS = List.of(
            "Ekim ayı hakediş ödemesi", "Fatura ödemesi", "Kira bedeli", "Tedarikçi ödemesi", "Maaş avansı",
            "Sipariş bedeli", "Hizmet bedeli", "Komisyon ödemesi", "Bayi alacak ödemesi", "Sigorta primi",
            "Eğitim ücreti", "Proje ilk taksit ödemesi");

    /** Hata kodu başına iki ayrıntılı hata açıklaması (kuyrukta ve detayda gösterilir). */
    private static final Map<ErrorCode, List<String>> ERROR_DETAILS = new EnumMap<>(ErrorCode.class);

    /** Hata kodu başına çözüm cümleleri. Çözüm metni bunlardan biri ve rastgele bir kapanış cümlesinden oluşur. */
    private static final Map<ErrorCode, List<String>> RESOLUTIONS = new EnumMap<>(ErrorCode.class);

    static final List<String> RESOLUTION_CLOSINGS = List.of(
            "", "", " Müşteri bilgilendirildi.", " Süreç kayıt altına alındı.",
            " İşlem sonucu sistemden kontrol edildi.", " Gönderen firma e-posta ile bilgilendirildi.");

    static final List<String> GENERIC_CAUSES = List.of(
            "Gönderen tarafın bilgiyi eksik veya hatalı girmesi",
            "Kayıtlı alıcı bilgisinin güncel olmaması",
            "Karşı bankadaki kaydın farklı biçimde tutulması",
            "Aynı talimatın farklı kanallardan tekrar gönderilmesi",
            "Dosya veya form hazırlanırken oluşan biçim hatası");

    static final List<String> GENERIC_STEPS = List.of(
            "Gönderen müşteri ile iletişime geçerek bilgiyi teyit edin.",
            "Talimatın geçmiş işlemlerini ve benzer kayıtları kontrol edin.",
            "Teyit alındıysa talimatı düzelterek yeniden gönderin.",
            "Teyit alınamazsa talimatı iade edin ve müşteriyi bilgilendirin.",
            "Karşı banka operasyon ekibinden durum teyidi isteyin.");

    static final List<String> GENERIC_WARNINGS = List.of(
            "Teyit yalnızca sistemde kayıtlı iletişim bilgisi üzerinden alınmalıdır.",
            "Benzer kayıtların bazılarında sonuç farklı çıkmıştır; her kayıt ayrıca değerlendirilmelidir.",
            "Tutar yüksekse takım lideriyle birlikte değerlendirme yapılması önerilir.");

    static {
        ERROR_DETAILS.put(ErrorCode.IBAN_CHECKSUM, List.of(
                "Alıcı IBAN'ının kontrol hanesi doğrulanamadı; IBAN yapısı geçersiz.",
                "IBAN mod-97 kontrolünden geçmedi, talimat işlenemedi."));
        ERROR_DETAILS.put(ErrorCode.NAME_MISMATCH, List.of(
                "Karşı banka alıcı unvanı doğrulamasında uyumsuzluk bildirdi. Talimattaki ad, hesap sahibinin kayıtlı adının bir kısmıyla eşleşiyor.",
                "Alıcı adı ile hesap sahibi adı arasındaki benzerlik skoru eşiğin altında kaldı."));
        ERROR_DETAILS.put(ErrorCode.DUPLICATE_SUSPECT, List.of(
                "Aynı gönderen, alıcı ve tutarla kısa süre önce işlenmiş bir talimat bulundu.",
                "Talimat son 24 saatteki bir kayıtla yüksek oranda eşleşiyor, mükerrer olabilir."));
        ERROR_DETAILS.put(ErrorCode.FAST_LIMIT, List.of(
                "Talimat tutarı tek işlem FAST limitini aşıyor.",
                "Gönderen müşterinin günlük FAST limiti aşıldı."));
        ERROR_DETAILS.put(ErrorCode.OUT_OF_HOURS, List.of(
                "Talimat EFT çalışma saatleri dışında alındı.",
                "Kurumsal talimat mesai bitiminden sonra gönderildi, bir sonraki seansa kalacak."));
        ERROR_DETAILS.put(ErrorCode.HOLIDAY_VALUE_DATE, List.of(
                "Talimatın valör tarihi resmi tatil gününe denk geliyor.",
                "Dosyadaki satırların valör tarihi iş günü değil."));
        ERROR_DETAILS.put(ErrorCode.ACCOUNT_BLOCKED, List.of(
                "Alıcı hesap bloke olduğu için karşı banka talimatı reddetti.",
                "Alıcı hesap kapalı olduğundan talimat işlenemedi."));
        ERROR_DETAILS.put(ErrorCode.SANCTION_HIT, List.of(
                "Alıcı adı yaptırım listesindeki bir kayıtla yüksek benzerlik gösteriyor.",
                "Tarama sistemi isim eşleşmesi buldu, manuel uyum incelemesi gerekiyor."));
        ERROR_DETAILS.put(ErrorCode.BATCH_FORMAT, List.of(
                "Toplu ödeme dosyasında bazı satırlar beklenen formata uymuyor.",
                "Dosyadaki tutar veya IBAN alanlarında biçim hatası var, dosya işlenemedi."));
        ERROR_DETAILS.put(ErrorCode.TIMEOUT, List.of(
                "Karşı bankadan yanıt alınamadı, işlemin son durumu belirsiz.",
                "Zaman aşımı oluştu, talimatın karşı tarafta gerçekleşip gerçekleşmediği bilinmiyor."));

        RESOLUTIONS.put(ErrorCode.IBAN_CHECKSUM, List.of(
                "Müşteri temsilcisi aracılığıyla doğru IBAN teyit edildi, talimat düzeltilerek yeniden gönderildi.",
                "Gönderen firmadan güncel IBAN bilgisi yazılı olarak alındı ve talimat yeni IBAN ile yeniden oluşturuldu.",
                "IBAN'daki hata, gönderenin bir haneyi eksik girmesinden kaynaklandı; doğru IBAN teyit edilip talimat tekrar işleme alındı.",
                "Alıcı IBAN'ı sistemdeki kayıtlı alıcı bilgisiyle karşılaştırıldı, hatalı hane düzeltilip talimat yeniden gönderildi."));
        RESOLUTIONS.put(ErrorCode.NAME_MISMATCH, List.of(
                "Şube müşteri temsilcisi aracılığıyla gönderen firma arandı, alıcının tam unvanı teyit edilerek talimat düzeltilip yeniden gönderildi.",
                "Alıcı adı çok kelimeli soyadın eksik girilmesi nedeniyle uyuşmuyordu; tam ad teyit edilip talimat güncellendi.",
                "Gönderenin eski alıcı kaydını kullandığı anlaşıldı, güncel unvan alınarak kayıt güncellendi ve talimat yeniden gönderildi.",
                "Alıcı unvanı teyit edilemedi, talimat iade edildi ve müşteri bilgilendirildi."));
        RESOLUTIONS.put(ErrorCode.DUPLICATE_SUSPECT, List.of(
                "Aynı tutar ve alıcıya ait iki talimat incelendi, ikincisinin mükerrer olduğu müşteriyle teyit edilerek iptal edildi.",
                "Talimatlar farklı fatura numaralarına ait olduğu için mükerrer değildi; müşteri onayı alınarak ikisi de işleme alındı.",
                "Mükerrer olduğu doğrulanan talimat iade edildi ve gönderen firma bilgilendirildi.",
                "Kanal kaynaklı çift gönderim tespit edildi, tekrarlanan talimat iptal edilip ilk talimat işleme alındı."));
        RESOLUTIONS.put(ErrorCode.FAST_LIMIT, List.of(
                "Talimat tutarı FAST limitini aştığı için müşteri bilgilendirildi ve ödeme EFT olarak yeniden oluşturuldu.",
                "Tutar limit altında kalacak şekilde iki talimata bölündü ve müşteri onayıyla yeniden gönderildi.",
                "Müşterinin günlük FAST limiti artırım talebi onaylandı, talimat yeniden işleme alındı.",
                "Gönderen kurumsal müşteriye limit aşımı bildirildi, talimat Havale olarak yeniden yönlendirildi."));
        RESOLUTIONS.put(ErrorCode.OUT_OF_HOURS, List.of(
                "Talimat EFT saatleri dışında geldiği için bir sonraki iş günü valörüyle işleme alındı ve müşteri bilgilendirildi.",
                "Müşteri talebiyle talimat ertesi iş günü açılışında gönderilmek üzere zamanlandı.",
                "Acil olmadığı teyit edilen talimat mesai başlangıcında işleme alındı.",
                "Kurumsal müşteriye EFT saatleri hatırlatıldı, talimat bir sonraki işlem gününe ertelendi."));
        RESOLUTIONS.put(ErrorCode.HOLIDAY_VALUE_DATE, List.of(
                "Valör tarihi resmi tatile denk geldiği için müşteri onayıyla valör ilk iş gününe çekildi.",
                "Talimatın valör tarihi bir önceki iş gününe alınarak ödeme zamanında gerçekleşti.",
                "Toplu ödeme dosyasındaki tatile denk gelen satırların valörü güncellenip dosya yeniden yüklendi.",
                "Müşteri tatil sonrası ilk iş gününe ertelemeyi onayladı, valör tarihi buna göre düzeltildi."));
        RESOLUTIONS.put(ErrorCode.ACCOUNT_BLOCKED, List.of(
                "Alıcı hesabın bloke olduğu karşı bankadan teyit edildi, talimat iade edilerek gönderen bilgilendirildi.",
                "Hesap kapalı olduğu için talimat iade edildi, müşteriden güncel alıcı hesap bilgisi istendi.",
                "Bloke kaldırıldıktan sonra karşı bankayla iletişime geçilerek talimat yeniden gönderildi.",
                "Alıcının yeni hesap bilgisi yazılı olarak alındı ve talimat yeni hesaba yönlendirildi."));
        RESOLUTIONS.put(ErrorCode.SANCTION_HIT, List.of(
                "İsim benzerliği uyum birimi tarafından incelendi, yanlış eşleşme olduğu belgelenerek talimat serbest bırakıldı.",
                "Eşleşme doğrulandığı için talimat uyum birimi kararıyla durduruldu ve gerekli bildirim yapıldı.",
                "Ek kimlik belgeleri istenerek eşleşmenin farklı bir kişiye ait olduğu teyit edildi, talimat işleme alındı.",
                "Kayıt uyum biriminin onayıyla incelendi ve talimat iade edilerek süreç tamamlandı."));
        RESOLUTIONS.put(ErrorCode.BATCH_FORMAT, List.of(
                "Toplu ödeme dosyasındaki hatalı satırlar ayıklandı, düzeltilen satırlarla dosya yeniden yüklendi.",
                "Dosyadaki tutar alanının ondalık ayracı düzeltilip dosya yeniden işlendi.",
                "Hatalı satırlar müşteriye bildirilerek düzeltilmiş dosya alındı ve ödeme tamamlandı.",
                "IBAN alanı eksik satırlar tespit edildi, müşteriden güncel liste alınarak dosya yeniden yüklendi."));
        RESOLUTIONS.put(ErrorCode.TIMEOUT, List.of(
                "Karşı bankadan işlem durumu teyit edildi, talimatın başarıyla tamamlandığı görüldü ve kayıt kapatıldı.",
                "Karşı banka yanıt vermediği için işlem durumu sorgulandı; talimat gerçekleşmediği için yeniden gönderildi.",
                "Belirsiz durumdaki talimat için mutabakat kaydı kontrol edilip çift işlem oluşmadığı doğrulandı.",
                "Karşı banka operasyon ekibiyle iletişime geçilerek talimatın reddedildiği öğrenildi, müşteri bilgilendirildi."));
    }

    static List<String> errorDetails(ErrorCode code) {
        return ERROR_DETAILS.get(code);
    }

    static List<String> resolutionSentences(ErrorCode code) {
        return RESOLUTIONS.get(code);
    }

    // ---- Minimal JSON üretimi (seed'deki AI analiz kayıtları için) ----

    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String jsonArray(List<String> items) {
        return items.stream().map(SeedTexts::quote).collect(Collectors.joining(",", "[", "]"));
    }
}
