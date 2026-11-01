package com.bankops.domain;

import java.time.LocalDate;

/** Resmi tatil takvimi. Domain bu arayüzü bilir, gerçek veri altyapı katmanından gelir. */
@FunctionalInterface
public interface HolidayCalendar {

    boolean isHoliday(LocalDate date);
}
