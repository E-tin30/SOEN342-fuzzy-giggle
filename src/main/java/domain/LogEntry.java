package domain;

import java.time.LocalDateTime;

public record LogEntry (
    Patient patient,
    Appointment appointment,
    LocalDateTime timestamp
){}
