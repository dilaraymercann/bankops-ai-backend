package com.bankops.infrastructure.config;

import com.bankops.domain.ApprovalPolicy;
import com.bankops.domain.CaseStateMachine;
import com.bankops.domain.HolidayCalendar;
import com.bankops.domain.SlaCalculator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Domain sınıfları Spring'e bağımlı olmadığı için bean olarak burada tanımlanır. */
@Configuration
public class DomainConfig {

    /** Zamana ihtiyaç duyan her kod bu bean'i kullanır; testlerde Clock.fixed(...) verilebilir. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CaseStateMachine caseStateMachine() {
        return new CaseStateMachine();
    }

    @Bean
    ApprovalPolicy approvalPolicy() {
        return new ApprovalPolicy();
    }

    @Bean
    SlaCalculator slaCalculator(SlaProperties properties, HolidayCalendar holidayCalendar) {
        return new SlaCalculator(
                properties.businessHoursStart(),
                properties.businessHoursEnd(),
                properties.timeZone(),
                holidayCalendar);
    }
}
