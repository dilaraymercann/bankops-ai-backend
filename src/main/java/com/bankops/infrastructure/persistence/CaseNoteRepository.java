package com.bankops.infrastructure.persistence;

import com.bankops.domain.CaseNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseNoteRepository extends JpaRepository<CaseNote, Long> {
}
