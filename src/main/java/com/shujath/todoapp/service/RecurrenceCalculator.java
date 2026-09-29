package com.shujath.todoapp.service;

import com.shujath.todoapp.entity.TodoItem.RecurrenceType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Calculates recurring todo occurrences. Pure date arithmetic, no clock involved,
 * so the same input always gives the same result.
 */
@Component
public class RecurrenceCalculator {

    public LocalDate nextOccurrence(RecurrenceType type, LocalDate current) {
        return nextOccurrence(type, current, current);
    }

    /**
     * @param type    recurrence type
     * @param current the occurrence to calculate the next one from
     * @param anchor  date the schedule started from; monthly recurrences land on its
     *                day-of-month, or the last day of the month when that day does not exist
     */
    public LocalDate nextOccurrence(RecurrenceType type, LocalDate current, LocalDate anchor) {
        if (type == null || current == null || anchor == null) {
            throw new IllegalArgumentException("Recurrence type, current date and anchor date are required");
        }

        return switch (type) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusWeeks(1);
            case MONTHLY -> {
                YearMonth nextMonth = YearMonth.from(current).plusMonths(1);
                int day = Math.min(anchor.getDayOfMonth(), nextMonth.lengthOfMonth());
                yield nextMonth.atDay(day);
            }
        };
    }
}
