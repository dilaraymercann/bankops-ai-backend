package com.bankops.application.cases;

import java.time.Instant;

public record NoteView(Long id, String authorName, String text, Instant createdAt) {
}
