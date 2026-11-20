package domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Availability {
    private Professional professional;
    private LocalDate date;
    private LocalTime start;
    private LocalTime end;
    private final List<Appointment> appointments = new ArrayList<>(); // already booked inside this block

    public Availability(Professional professional, LocalDate date, LocalTime start, LocalTime end) {
        this.professional = professional;
        this.date = date;
        this.start = start;
        this.end = end;
    }

    // null range means "no filter"
    public boolean isWithin(DateRange range) {
        if (range == null) return true;
        return !date.isBefore(range.from()) && !date.isAfter(range.to());
    }

    // true if [from, from + minutes) is inside this block and doesn't overlap a booked appointment
    public boolean canFit(LocalTime from, int minutes) {
        if (from == null || minutes <= 0) return false;
        LocalTime to = from.plusMinutes(minutes);
        if (!to.isAfter(from) || from.isBefore(start) || to.isAfter(end)) return false;
        for (Appointment a : appointments) {
            if (from.isBefore(a.getEnd()) && to.isAfter(a.getStart())) return false;
        }
        return true;
    }

    // earliest start time where an appointment of that length fits, or null if none does
    public LocalTime firstFit(int minutes) {
        LocalTime best = canFit(start, minutes) ? start : null;
        for (Appointment a : appointments) {
            LocalTime candidate = a.getEnd();
            if (canFit(candidate, minutes) && (best == null || candidate.isBefore(best))) {
                best = candidate;
            }
        }
        return best;
    }

    // returns null if the appointment doesn't fit (someone else took that time)
    public Appointment createAppointment(Patient patient, Service service, Referral referral, Clinic clinic, LocalTime from, int minutes) {
        if (!canFit(from, minutes)) {
            return null;
        }
        Appointment appointment = new Appointment(professional, patient, referral,clinic, service, date, from, from.plusMinutes(minutes));
        appointments.add(appointment);
        return appointment;
    }


    public boolean overlaps(Availability other) {
        if (other == null) return false;

        //Different professionals are allowed to have conflicting availabilities
        if (this.professional.getId() != other.getProfessional().getId()) {
            return false;
        }

        //Different dates cannot overlap
        if (!this.date.equals(other.getDate())) {
            return false;
        }

        //Check if the time intervals overlap
        return this.start.isBefore(other.getEnd()) && this.end.isAfter(other.getStart());
    }

}
