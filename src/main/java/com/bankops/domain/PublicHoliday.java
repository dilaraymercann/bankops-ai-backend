package com.bankops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "public_holidays")
@Getter
@Setter
@NoArgsConstructor
public class PublicHoliday {

    @Id
    @Column(name = "holiday_date")
    private LocalDate date;

    @Column(nullable = false)
    private String name;
}
