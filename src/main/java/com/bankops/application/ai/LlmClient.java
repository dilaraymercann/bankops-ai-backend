package com.bankops.application.ai;

/** Sohbet modeline JSON üreten bir çağrı yapar. Sağlayıcıdan bağımsız arayüz (bkz. {@link TextEmbedder}). */
public interface LlmClient {

    boolean isConfigured();

    /** Modelden yalnızca JSON metni ister; hangi modelin yanıtladığı da döner (yedek modele düşülmüş olabilir). */
    Reply generateJson(String systemPrompt, String userPrompt);

    record Reply(String text, String modelName) {
    }
}
