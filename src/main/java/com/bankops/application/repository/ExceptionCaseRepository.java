package com.bankops.application.repository;

import com.bankops.domain.CaseStatus;
import com.bankops.domain.ExceptionCase;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@link JpaSpecificationExecutor}: kuyruktaki dinamik filtreler Specification olarak birleştirilir. */
public interface ExceptionCaseRepository
        extends JpaRepository<ExceptionCase, Long>, JpaSpecificationExecutor<ExceptionCase> {

    /**
     * Kaydı değiştirmeden önce satırı kilitler: aynı anda iki kişi aynı kaydı onaylamaya ya da
     * değiştirmeye çalışırsa ikincisi birincisinin bitmesini bekler ve güncel durumu görür.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ExceptionCase c where c.id = :id")
    Optional<ExceptionCase> findByIdForUpdate(@Param("id") Long id);

    List<ExceptionCase> findByStatusOrderByUpdatedAtAsc(CaseStatus status);

    /** SLA süresi dolmuş ama henüz eskale edilmemiş açık kayıtlar. */
    List<ExceptionCase> findByEscalatedFalseAndSlaDeadlineBeforeAndStatusIn(Instant now, Collection<CaseStatus> statuses);
}
