package domain.pattern;

import java.time.LocalDate;
import java.util.Set;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MonthlyPattern implements RepetitionPattern{
    private final Set<Integer> daysOfMonth;

    @Override
    public boolean matches(LocalDate date) {
        return daysOfMonth.contains(date.getDayOfMonth());
    }
}
