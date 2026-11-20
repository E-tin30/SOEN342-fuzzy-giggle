package domain;

import java.time.LocalDate;

public class DailyPattern implements RepetitionPattern {
    @Override
    public boolean matches(LocalDate date) {
        return true;
    }   
}
