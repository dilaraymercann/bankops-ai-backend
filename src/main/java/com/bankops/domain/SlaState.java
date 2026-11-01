package com.bankops.domain;

/** SLA'nın görsel durumu (CLAUDE.md §9): Normal (gri), Yaklaşıyor (amber), Aşıldı (kırmızı). */
public enum SlaState {
    Normal,
    Approaching,
    Breached
}
