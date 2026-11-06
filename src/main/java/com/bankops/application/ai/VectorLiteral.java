package com.bankops.application.ai;

/** pgvector'ün metin biçimi: {@code [0.1,0.2,...]}; SQL'de {@code ?::vector} ile kullanılır. */
final class VectorLiteral {

    private VectorLiteral() {
    }

    static String of(float[] vector) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }
}
