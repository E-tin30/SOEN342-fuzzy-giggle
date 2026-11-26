package services;

import database.DatabaseGateway;
import database.WaitlistRow;
import domain.Clinic;
import domain.DateRange;
import domain.Patient;
import domain.Professional;
import domain.WaitlistEntry;
import domain.WaitlistStatus;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class WaitlistService {
    private final DatabaseGateway db;

    public WaitlistEntry add(Patient patient, Professional professional, String specialization,
                             DateRange range, int priority) throws SQLException {
        if (patient == null || patient.getId() <= 0) {
            throw new IllegalArgumentException("A registered patient is required.");
        }
        boolean hasSpecialization = specialization != null && !specialization.isBlank();
        if (professional == null && !hasSpecialization) {
            throw new IllegalArgumentException("Choose a professional or a specialization.");
        }
        checkPriority(priority);

        String spec = hasSpecialization ? specialization.trim() : null;
        int id = db.addWaitlistEntry(patient.getId(), professional == null ? null : professional.getId(),
                spec, range == null ? null : range.from(), range == null ? null : range.to(), priority);
        return new WaitlistEntry(id, patient, professional, spec, range, priority, WaitlistStatus.WAITING);
    }

    // active entries, highest priority first
    public List<WaitlistEntry> getActiveEntries(Clinic clinic) throws SQLException {
        List<WaitlistEntry> entries = new ArrayList<>();
        for (WaitlistRow r : db.findActiveWaitlist()) {
            Patient patient = findPatient(clinic, r.patientId());
            if (patient == null) continue;

            Professional professional = null;
            if (r.professionalId() != null) {
                professional = findProfessional(clinic, r.professionalId());
                if (professional == null) continue;
            }

            DateRange range = null;
            if (r.dateFrom() != null && r.dateTo() != null) {
                range = new DateRange(LocalDate.parse(r.dateFrom()), LocalDate.parse(r.dateTo()));
            }
            entries.add(new WaitlistEntry(r.id(), patient, professional, r.specialization(), range,
                    r.priority(), WaitlistStatus.valueOf(r.status())));
        }
        return entries;
    }

    public void changePriority(WaitlistEntry entry, int priority) throws SQLException {
        checkPriority(priority);
        if (!db.setWaitlistPriority(entry.getId(), priority)) {
            throw new IllegalStateException("This waitlist entry no longer exists.");
        }
        entry.setPriority(priority);
    }

    public void remove(WaitlistEntry entry) throws SQLException {
        if (!db.setWaitlistStatus(entry.getId(), WaitlistStatus.REMOVED.name())) {
            throw new IllegalStateException("This waitlist entry no longer exists.");
        }
        entry.setStatus(WaitlistStatus.REMOVED);
    }

    private void checkPriority(int priority) {
        if (priority < 1 || priority > 5) {
            throw new IllegalArgumentException("Priority must be between 1 (highest) and 5 (lowest).");
        }
    }

    private Patient findPatient(Clinic clinic, int id) {
        for (Patient p : clinic.getPatients()) {
            if (p.getId() == id) return p;
        }
        return null;
    }

    private Professional findProfessional(Clinic clinic, int id) {
        for (Professional p : clinic.getProfessionals()) {
            if (p.getId() == id) return p;
        }
        return null;
    }

    // waiting entries that match an opening with this professional on this date, highest priority first
    public List<WaitlistEntry> findEligible(Clinic clinic, Professional professional, LocalDate date)
            throws SQLException {
        List<WaitlistEntry> eligible = new ArrayList<>();
        for (WaitlistEntry w : getActiveEntries(clinic)) {
            if (w.getStatus() == WaitlistStatus.WAITING && matches(w, professional, date)) {
                eligible.add(w);
            }
        }
        return eligible;
    }

    private boolean matches(WaitlistEntry w, Professional professional, LocalDate date) {
        if (w.getProfessional() != null) {
            if (w.getProfessional().getId() != professional.getId()) return false;
        } else {
            String spec = professional.getSpecialization();
            if (spec == null || !spec.equalsIgnoreCase(w.getSpecialization())) return false;
        }
        DateRange range = w.getDateRange();
        return range == null || (!date.isBefore(range.from()) && !date.isAfter(range.to()));
    }

    public void markOffered(WaitlistEntry entry) throws SQLException {
        setStatus(entry, WaitlistStatus.OFFERED);
    }

    public void markBooked(WaitlistEntry entry) throws SQLException {
        setStatus(entry, WaitlistStatus.BOOKED);
    }

    // the patient said no, or nothing was booked: back to waiting, same priority
    public void declineOffer(WaitlistEntry entry) throws SQLException {
        setStatus(entry, WaitlistStatus.WAITING);
    }

    private void setStatus(WaitlistEntry entry, WaitlistStatus status) throws SQLException {
        if (!db.setWaitlistStatus(entry.getId(), status.name())) {
            throw new IllegalStateException("This waitlist entry no longer exists.");
        }
        entry.setStatus(status);
    }
}