package controllers;

import domain.Clinic;
import domain.DateRange;
import domain.Patient;
import domain.Professional;
import domain.WaitlistEntry;
import lombok.RequiredArgsConstructor;
import services.WaitlistService;

import java.sql.SQLException;
import java.util.List;

@RequiredArgsConstructor
public class WaitlistController {
    private final Clinic clinic;
    private final WaitlistService waitlistService;

    public WaitlistEntry addToWaitlist(Patient patient, Professional professional, String specialization,
                                       DateRange range, int priority) throws SQLException {
        return waitlistService.add(patient, professional, specialization, range, priority);
    }

    public List<WaitlistEntry> viewWaitlist() throws SQLException {
        return waitlistService.getActiveEntries(clinic);
    }

    public void changePriority(WaitlistEntry entry, int priority) throws SQLException {
        waitlistService.changePriority(entry, priority);
    }

    public void removeFromWaitlist(WaitlistEntry entry) throws SQLException {
        waitlistService.remove(entry);
    }
}