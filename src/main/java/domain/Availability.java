package domain;

import lombok.Getter;
import lombok.Setter;

import java.sql.Time;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class Availability {
    private Professional professional;
    private Date date;
    private Time start;
    private Time end;
    private final List<Appointment> appointments = new ArrayList<>(); // already booked inside this block

    public Availability(Professional professional, Date date, Time start, Time end) {
        this.professional = professional;
        this.date = date;
        this.start = start;
        this.end = end;
    }

    // null range means "no filter"
    public boolean isWithin(DateRange range) {
        if (range == null) return true;
        return !date.before(range.from()) && !date.after(range.to());
    }

    private static Time plus(Time time, int minutes) {
        return new Time(time.getTime() + minutes * 60_000L);
    }

    // true if [from, from + minutes) is inside this block and doesn't overlap a booked appointment
    public boolean canFit(Time from, int minutes) {
        if (minutes <= 0) return false;
        Time to = plus(from, minutes);
        if (from.before(start) || to.after(end)) return false;
        for (Appointment a : appointments) {
            if (from.before(a.getEnd()) && to.after(a.getStart())) return false;
        }
        return true;
    }

    // earliest start time where an appointment of that length fits, or null if none does
    public Time firstFit(int minutes) {
        Time best = canFit(start, minutes) ? start : null;
        for (Appointment a : appointments) {
            Time candidate = a.getEnd();
            if (canFit(candidate, minutes) && (best == null || candidate.before(best))) {
                best = candidate;
            }
        }
        return best;
    }

    // returns null if the appointment doesn't fit (someone else took that time)
    public Appointment createAppointment(Patient patient, Service service, Referral referral,
                                         Clinic clinic, Time from, int minutes) {
        if (!canFit(from, minutes)) {
            return null;
        }
        Appointment appointment = new Appointment(professional, patient, referral,
                clinic, service, date, from, plus(from, minutes));
        appointments.add(appointment);
        return appointment;
    }
}
