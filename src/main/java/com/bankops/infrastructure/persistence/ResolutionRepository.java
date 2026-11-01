package com.bankops.infrastructure.persistence;

import com.bankops.domain.Resolution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResolutionRepository extends JpaRepository<Resolution, Long> {
}
