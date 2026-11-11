package domain;

import lombok.Getter;
import lombok.Setter;

import java.sql.Time;
import java.util.Date;

@Getter
@Setter
public class Availability {
    private Professional professional;
    private Date date;
    private Time start;
    private Time end;
    private boolean booked = false;

    public Availability(Professional professional, Date date, Time start, Time end) {
        this.professional = professional;
        this.date = date;
        this.start = start;
        this.end = end;
    }

    // null range means "no filter"
    public boolean isWithin(DateRange range) {
        if (range == null) return true;
        return !this.date.before(range.from()) && !this.date.after(range.to());
    }

    // create an appointment
    public Appointment createAppointment(Patient patient, Service service, Referral referral, Clinic clinic) {
        if (booked) {
            return null;   // someone else booked it first
        }
        Appointment appointment = new Appointment(professional, patient, referral, clinic, service, date, start, end);
        booked = true;
        return appointment;
    }
}