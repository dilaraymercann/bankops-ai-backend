package com.bankops.application.ai;

/** Model iki denemede de şemaya uygun yanıt üretemedi (API'de 502). */
public class AiAnalysisFailedException extends RuntimeException {

    public AiAnalysisFailedException(String message) {
        super(message);
    }
}
