package com.shujath.todoapp.service;

import com.shujath.todoapp.entity.TodoItem.RecurrenceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurrenceCalculatorTest {

    private final RecurrenceCalculator calculator = new RecurrenceCalculator();

    @ParameterizedTest
    @CsvSource({
            "2026-03-10, 2026-03-11",
            "2026-01-31, 2026-02-01", // month boundary
            "2026-12-31, 2027-01-01", // year boundary
            "2028-02-28, 2028-02-29", // leap day
            "2027-02-28, 2027-03-01"  // non-leap year
    })
    void dailyMovesToNextCalendarDay(LocalDate current, LocalDate expected) {
        assertThat(calculator.nextOccurrence(RecurrenceType.DAILY, current)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-03-10, 2026-03-17",
            "2026-01-28, 2026-02-04", // month boundary
            "2026-12-29, 2027-01-05", // year boundary
            "2028-02-26, 2028-03-04"  // across leap day
    })
    void weeklyMovesToSameDayNextWeek(LocalDate current, LocalDate expected) {
        LocalDate next = calculator.nextOccurrence(RecurrenceType.WEEKLY, current);

        assertThat(next).isEqualTo(expected);
        assertThat(next.getDayOfWeek()).isEqualTo(current.getDayOfWeek());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-03-10, 2026-04-10",
            "2026-12-15, 2027-01-15", // year boundary
            "2026-01-31, 2026-02-28", // non-leap February
            "2028-01-31, 2028-02-29", // leap February
            "2028-01-30, 2028-02-29",
            "2026-01-29, 2026-02-28",
            "2026-03-31, 2026-04-30", // 30-day month
            "2026-05-31, 2026-06-30",
            "2026-08-31, 2026-09-30",
            "2026-10-31, 2026-11-30",
            "2026-07-31, 2026-08-31"  // both months have 31 days
    })
    void monthlyUsesCorrespondingDayOrLastDayOfNextMonth(LocalDate current, LocalDate expected) {
        assertThat(calculator.nextOccurrence(RecurrenceType.MONTHLY, current)).isEqualTo(expected);
    }

    @Test
    void monthlyReturnsToAnchorDayAfterShortMonth() {
        LocalDate anchor = LocalDate.of(2026, 1, 31);

        LocalDate feb = calculator.nextOccurrence(RecurrenceType.MONTHLY, anchor, anchor);
        LocalDate mar = calculator.nextOccurrence(RecurrenceType.MONTHLY, feb, anchor);
        LocalDate apr = calculator.nextOccurrence(RecurrenceType.MONTHLY, mar, anchor);
        LocalDate may = calculator.nextOccurrence(RecurrenceType.MONTHLY, apr, anchor);

        assertThat(feb).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(mar).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(apr).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(may).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void monthlyAnchoredOnLeapDayFallsBackInNonLeapYears() {
        LocalDate anchor = LocalDate.of(2028, 2, 29);
        LocalDate current = LocalDate.of(2029, 1, 29);

        assertThat(calculator.nextOccurrence(RecurrenceType.MONTHLY, current, anchor))
                .isEqualTo(LocalDate.of(2029, 2, 28));
        assertThat(calculator.nextOccurrence(RecurrenceType.MONTHLY, LocalDate.of(2029, 2, 28), anchor))
                .isEqualTo(LocalDate.of(2029, 3, 29));
    }

    @Test
    void anchorDoesNotAffectDailyOrWeekly() {
        LocalDate anchor = LocalDate.of(2026, 1, 31);
        LocalDate current = LocalDate.of(2026, 2, 28);

        assertThat(calculator.nextOccurrence(RecurrenceType.DAILY, current, anchor))
                .isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(calculator.nextOccurrence(RecurrenceType.WEEKLY, current, anchor))
                .isEqualTo(LocalDate.of(2026, 3, 7));
    }

    @Test
    void isDeterministic() {
        LocalDate date = LocalDate.of(2026, 1, 31);

        for (RecurrenceType type : RecurrenceType.values()) {
            assertThat(calculator.nextOccurrence(type, date))
                    .isEqualTo(calculator.nextOccurrence(type, date));
        }
    }

    @Test
    void rejectsMissingInput() {
        LocalDate date = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> calculator.nextOccurrence(null, date))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.nextOccurrence(RecurrenceType.DAILY, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.nextOccurrence(RecurrenceType.MONTHLY, date, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
