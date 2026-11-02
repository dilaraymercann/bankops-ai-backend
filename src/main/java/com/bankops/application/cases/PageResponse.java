package com.bankops.application.cases;

import java.util.List;

/** Sayfalı liste yanıtı. {@code page} sıfırdan başlar. */
public record PageResponse<T>(List<T> items, int page, int pageSize, long totalItems, int totalPages) {
}
