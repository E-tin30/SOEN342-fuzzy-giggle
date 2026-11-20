package domain;

import java.util.Set;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MonthlyPattern implements RepetitionPattern{
    private final Set<Integer> daysOfMonth;
}
