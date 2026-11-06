-- Faz 4: bilgi tabanı çözümlerinin anlamsal arama için embedding'i.
-- 768 boyut: gemini-embedding-2 çıktısı outputDimensionality=768 ile küçültülür (CLAUDE.md §17).
-- Boş (NULL) embedding: çözüm henüz embed edilmemiştir; benzerlik aramasında yok sayılır.
ALTER TABLE resolutions ADD COLUMN embedding vector(768);

-- Bilinçli olarak vektör indeksi (HNSW/IVFFlat) yok: bilgi tabanı birkaç yüz satırdır ve sorgu önce hata koduna
-- göre süzülür. Tam tarama hem hızlıdır hem de yaklaşık indeksin filtreyle birlikte sonuç kaçırma riskini taşımaz.
