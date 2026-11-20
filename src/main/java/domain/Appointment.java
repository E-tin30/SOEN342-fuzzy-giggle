package domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Appointment {
    private final Professional professional;
    private final Patient patient;
    private final Referral referral;
    private final Clinic clinic;
    private final Service service;
    private final LocalDate date;
    private final LocalTime start;
    private final LocalTime end;
    @Setter
    private AppointmentStatus status = AppointmentStatus.PENDING;

    public Appointment(Professional professional, Patient patient, Referral referral, Clinic clinic, Service service, LocalDate date, LocalTime start, LocalTime end) {
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
        return Math.toIntExact(Duration.between(start, end).toMinutes());
    }

    public void confirmAppointment() {
        setStatus(AppointmentStatus.CONFIRMED);
    }

    public void cancelAppointment() {
        setStatus(AppointmentStatus.CANCELLED);
    }
}
