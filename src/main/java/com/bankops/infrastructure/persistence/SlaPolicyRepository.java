package com.bankops.infrastructure.persistence;

import com.bankops.domain.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {
}
