package com.bankops.application.repository;

import com.bankops.domain.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdAndActionOrderByTimestampAsc(String entityType, Long entityId, String action);
}
