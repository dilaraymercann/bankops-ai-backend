package com.bankops.infrastructure.sla;

import com.bankops.domain.HolidayCalendar;
import com.bankops.domain.PublicHoliday;
import com.bankops.infrastructure.persistence.PublicHolidayRepository;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resmi tatilleri {@code public_holidays} tablosundan okur ve bellekte tutar. */
@Component
@RequiredArgsConstructor
public class DatabaseHolidayCalendar implements HolidayCalendar {

    private final PublicHolidayRepository repository;

    private volatile Set<LocalDate> cache;

    @Override
    public boolean isHoliday(LocalDate date) {
        Set<LocalDate> dates = cache;
        if (dates == null) {
            dates = load();
        }
        return dates.contains(date);
    }

    /** Tablo değiştiğinde (ör. seed sonrası) önbelleği yeniler. */
    public synchronized void reload() {
        cache = null;
    }

    private synchronized Set<LocalDate> load() {
        if (cache == null) {
            cache = repository.findAll().stream().map(PublicHoliday::getDate).collect(Collectors.toUnmodifiableSet());
        }
        return cache;
    }
}
