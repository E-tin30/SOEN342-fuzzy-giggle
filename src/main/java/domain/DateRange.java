package domain;

import java.time.LocalDate;

public record DateRange(LocalDate from, LocalDate to) {
    public DateRange {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Date range dates cannot be null.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }
    }
}
