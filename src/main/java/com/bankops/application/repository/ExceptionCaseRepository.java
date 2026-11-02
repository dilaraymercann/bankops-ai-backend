package com.bankops.application.repository;

import com.bankops.domain.ExceptionCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** {@link JpaSpecificationExecutor}: kuyruktaki dinamik filtreler Specification olarak birleştirilir. */
public interface ExceptionCaseRepository
        extends JpaRepository<ExceptionCase, Long>, JpaSpecificationExecutor<ExceptionCase> {
}
