package domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class WeeklyPattern implements RepetitionPattern{
    private final Set<DayOfWeek> daysOfWeek;

    @Override
    public boolean matches(LocalDate date) {
        return daysOfWeek.contains(date.getDayOfWeek());
    }
}
