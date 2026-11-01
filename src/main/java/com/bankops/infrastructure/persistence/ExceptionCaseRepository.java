package com.bankops.infrastructure.persistence;

import com.bankops.domain.ExceptionCase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExceptionCaseRepository extends JpaRepository<ExceptionCase, Long> {
}
