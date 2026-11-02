package com.bankops.application.repository;

import com.bankops.domain.StatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByCaseIdOrderByChangedAtAsc(Long caseId);
}
