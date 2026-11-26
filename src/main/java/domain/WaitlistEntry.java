package domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
public class WaitlistEntry {
    private final int id;
    private final Patient patient;
    private final Professional professional; //null when waiting for a specialization
    private final String specialization; //null when waiting for a specific professional
    private final DateRange dateRange; //null means any date
    @Setter
    private int priority; //1 = highest, 5 = lowest
    @Setter
    private WaitlistStatus status;
}