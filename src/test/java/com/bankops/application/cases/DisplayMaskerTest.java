package com.bankops.application.cases;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DisplayMaskerTest {

    @Test
    void masksIbanKeepingCountryCheckDigitsAndLastSixCharacters() {
        assertThat(DisplayMasker.iban("TR330006100519786457841326")).isEqualTo("TR33 **** **** **** **** 8413 26");
    }

    @Test
    void masksSpacedAndLowercaseIbanTheSameWay() {
        assertThat(DisplayMasker.iban("tr33 0006 1005 1978 6457 8413 26")).isEqualTo(DisplayMasker.iban("TR330006100519786457841326"));
    }

    @Test
    void masksMalformedIbanCompletely() {
        assertThat(DisplayMasker.iban("TR12")).isEqualTo("****");
        assertThat(DisplayMasker.iban(null)).isEqualTo("****");
    }

    @Test
    void masksPersonNamesToFirstLetters() {
        assertThat(DisplayMasker.name("Mehmet Yılmaz")).isEqualTo("M*** Y***");
        assertThat(DisplayMasker.name("Ayşe Nur Kaya")).isEqualTo("A*** N*** K***");
    }

    @Test
    void keepsCompanyNamesReadable() {
        assertThat(DisplayMasker.name("Anadolu Hasat Tarım Ltd. Şti.")).isEqualTo("Anadolu Hasat Tarım Ltd. Şti.");
        assertThat(DisplayMasker.name("Marmara Yıldız Tekstil A.Ş.")).isEqualTo("Marmara Yıldız Tekstil A.Ş.");
    }

    @Test
    void handlesEmptyNames() {
        assertThat(DisplayMasker.name(null)).isEmpty();
        assertThat(DisplayMasker.name("   ")).isEmpty();
    }

    @Test
    void maskedOutputNeverContainsTheOriginalSurname() {
        assertThat(DisplayMasker.name("Mehmet Yılmaz")).doesNotContain("Yılmaz", "ılmaz");
    }
}
