package com.bankops.application.repository;

import com.bankops.domain.Resolution;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResolutionRepository extends JpaRepository<Resolution, Long> {

    /** Onay bekleyen (henüz doğrulanmamış) çözümler; revizyon sonrası aynı kayıtta birden fazla olabilir. */
    List<Resolution> findByCaseIdInAndVerifiedFalse(Collection<Long> caseIds);

    Optional<Resolution> findFirstByCaseIdAndVerifiedFalseOrderByCreatedAtDesc(Long caseId);
}
