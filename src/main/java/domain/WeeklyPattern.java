package domain;

import java.time.DayOfWeek;
import java.util.Set;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class WeeklyPattern implements RepetitionPattern{
    private final Set<DayOfWeek> daysOfWeek;
}
