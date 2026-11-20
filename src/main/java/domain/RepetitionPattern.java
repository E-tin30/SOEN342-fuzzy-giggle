package domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public interface RepetitionPattern {
    boolean matches(LocalDate date);

    default List<LocalDate> generateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Invalid date range.");
        }

        List<LocalDate> dates = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (matches(date)) {
                dates.add(date);
            }
        }

        return dates;
    }
}



