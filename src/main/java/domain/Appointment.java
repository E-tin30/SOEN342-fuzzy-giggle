package domain;

import java.sql.Time;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Appointment {
    private final Professional professional;
    private final Patient patient;
    private final Referral referral;
    private final Clinic clinic;
    private final Service service;
    private final Date date;
    private final Time start;
    private final Time end;
    @Setter
    private AppointmentStatus status = AppointmentStatus.PENDING;

    public Appointment(Professional professional, Patient patient, Referral referral,
                       Clinic clinic, Service service, Date date, Time start, Time end) {
        this.professional = professional;
        this.patient = patient;
        this.referral = referral;
        this.clinic = clinic;
        this.service = service;
        this.date = date;
        this.start = start;
        this.end = end;
    }

    public int getDurationMinutes() {
        return (int) ((end.getTime() - start.getTime()) / 60_000L);
    }

    public void confirmAppointment() {
        setStatus(AppointmentStatus.CONFIRMED);
    }

    public void cancelAppointment() {
        setStatus(AppointmentStatus.CANCELLED);
    }
}