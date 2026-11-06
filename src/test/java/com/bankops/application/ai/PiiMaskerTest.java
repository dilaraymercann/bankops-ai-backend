package com.bankops.application.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    @Test
    void masksIbanWithAndWithoutSpaces() {
        PiiMasker.Result spaced = masker.mask("Alıcı hesap TR49 0001 2345 6789 0123 4567 21 olarak girilmiş");
        PiiMasker.Result compact = masker.mask("IBAN: TR490001234567890123456721");

        assertThat(spaced.text()).isEqualTo("Alıcı hesap [IBAN] olarak girilmiş");
        assertThat(compact.text()).isEqualTo("IBAN: [IBAN]");
    }

    @Test
    void masksLowercaseIban() {
        assertThat(masker.mask("tr490001234567890123456721").text()).isEqualTo("[IBAN]");
    }

    @Test
    void masksTckn() {
        PiiMasker.Result result = masker.mask("Müşteri TCKN 12345678901 ile doğrulandı");

        assertThat(result.text()).isEqualTo("Müşteri TCKN [TCKN] ile doğrulandı");
        assertThat(result.maskedCount()).isEqualTo(1);
    }

    @Test
    void masksTcknStartingWithFive() {
        assertThat(masker.mask("kimlik 52345678901").text()).isEqualTo("kimlik [TCKN]");
    }

    @Test
    void masksPhoneNumbersInCommonFormats() {
        assertThat(masker.mask("Aranan numara 0532 123 45 67").text()).isEqualTo("Aranan numara [TELEFON]");
        assertThat(masker.mask("Aranan numara +90 532 123 45 67").text()).isEqualTo("Aranan numara [TELEFON]");
        assertThat(masker.mask("Aranan numara 05321234567").text()).isEqualTo("Aranan numara [TELEFON]");
        assertThat(masker.mask("Aranan numara 532-123-45-67").text()).isEqualTo("Aranan numara [TELEFON]");
    }

    @Test
    void masksSenderAndReceiverNamesCaseInsensitively() {
        PiiMasker.Result result = masker.mask(
                "Gönderen Fatma Bulut, alıcı ZİRVE DOĞAL KİMYA A.Ş. olarak geçiyor",
                "Fatma Bulut", "Zirve Doğal Kimya A.Ş.");

        assertThat(result.text()).isEqualTo("Gönderen [GÖNDEREN], alıcı [ALICI] olarak geçiyor");
        assertThat(result.maskedCount()).isEqualTo(2);
    }

    @Test
    void countsEveryMaskedField() {
        PiiMasker.Result result = masker.mask(
                "TR490001234567890123456721 hesabı, 12345678901 kimlikli Ali Veli, tel 0532 123 45 67",
                "Ali Veli", null);

        assertThat(result.maskedCount()).isEqualTo(4);
        assertThat(result.text()).doesNotContain("TR49", "12345678901", "Ali Veli", "0532");
    }

    @Test
    void leavesNonPersonalTextUntouched() {
        String text = "EXC-2026-12375 kaydı, 5.071,97 TL tutarında, valör 31.10.2026, referans TRX-2026-102375, Sigorta primi";

        PiiMasker.Result result = masker.mask(text, "Fatma Bulut", "Zirve Doğal Kimya A.Ş.");

        assertThat(result.text()).isEqualTo(text);
        assertThat(result.maskedCount()).isZero();
    }

    @Test
    void handlesNullAndBlankText() {
        assertThat(masker.mask(null).text()).isEmpty();
        assertThat(masker.mask("  ").maskedCount()).isZero();
    }

    @Test
    void ignoresBlankNames() {
        assertThat(masker.mask("kayıt metni", " ", null).text()).isEqualTo("kayıt metni");
    }
}
