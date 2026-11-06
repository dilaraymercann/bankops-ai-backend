package com.bankops.application.ai;

import java.util.List;

/**
 * Metni sayısal vektöre çevirir. Uygulama belirli bir sağlayıcıya bağımlı olmasın diye arayüzdür
 * (gerçek implementasyon: infrastructure/ai/GeminiClient; testlerde sahte implementasyon kullanılır).
 */
public interface TextEmbedder {

    /** API anahtarı tanımlı mı? Değilse AI özellikleri devre dışı kalır, uygulama yine çalışır. */
    boolean isConfigured();

    /** Arama sorgusu için embedding (kayıt metni). */
    float[] embedQuery(String text);

    /** Bilgi tabanına girecek belgeler (çözümler) için embedding; toplu çağrı. */
    List<float[]> embedDocuments(List<String> texts);
}
