package domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor 
@Getter 
public class ActivityLog {
    private final List<LogEntry> logEntries = new ArrayList<>();

    public void record(Patient patient, Appointment appointment){
        logEntries.add(new LogEntry(patient, appointment, LocalDateTime.now()));
    }
}
