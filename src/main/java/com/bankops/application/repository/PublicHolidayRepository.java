package com.bankops.application.repository;

import com.bankops.domain.PublicHoliday;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, LocalDate> {
}
