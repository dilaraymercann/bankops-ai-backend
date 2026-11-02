package com.bankops.application.repository;

import com.bankops.domain.CaseNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseNoteRepository extends JpaRepository<CaseNote, Long> {

    List<CaseNote> findByCaseIdOrderByCreatedAtAsc(Long caseId);
}
