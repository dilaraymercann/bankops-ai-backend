-- pgvector eklentisi: AI benzerlik araması için Resolution.Embedding sütununda kullanılacak (Faz 4).
-- Tablolar Faz 1'de V2 ve sonraki migration'larla eklenir.
CREATE EXTENSION IF NOT EXISTS vector;
