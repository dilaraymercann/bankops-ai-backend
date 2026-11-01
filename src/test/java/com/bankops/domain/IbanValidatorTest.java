package com.bankops.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbanValidatorTest {

    // Türkiye için yaygın kullanılan örnek IBAN
    private static final String VALID = "TR330006100519786457841326";

    @Test
    void acceptsValidIban() {
        assertThat(IbanValidator.isValid(VALID)).isTrue();
    }

    @Test
    void acceptsSpacedAndLowercaseIban() {
        assertThat(IbanValidator.isValid("tr33 0006 1005 1978 6457 8413 26")).isTrue();
    }

    @Test
    void rejectsWrongCheckDigits() {
        assertThat(IbanValidator.isValid("TR340006100519786457841326")).isFalse();
    }

    @Test
    void rejectsChangedAccountDigit() {
        assertThat(IbanValidator.isValid("TR330006100519786457841327")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TR33000610051978645784132", "TR3300061005197864578413267", "", "TR"})
    void rejectsWrongLength(String iban) {
        assertThat(IbanValidator.isValid(iban)).isFalse();
    }

    @Test
    void rejectsNonTurkishAndNull() {
        assertThat(IbanValidator.isValid("DE89370400440532013000")).isFalse();
        assertThat(IbanValidator.isValid(null)).isFalse();
    }

    @Test
    void generatedIbanIsValid() {
        String iban = IbanValidator.generate("00061", "00519786457841326".substring(0, 17));
        assertThat(iban).hasSize(26).startsWith("TR");
        assertThat(IbanValidator.isValid(iban)).isTrue();
    }

    @Test
    void generateRejectsWrongBbanLength() {
        assertThatThrownBy(() -> IbanValidator.generate("123", "456"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
