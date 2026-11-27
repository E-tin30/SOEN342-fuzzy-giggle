package controllers;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import domain.*;
import lombok.RequiredArgsConstructor;
import services.AvailabilityService;
import services.AppointmentService;
import utils.ConsolePrinter;

@RequiredArgsConstructor
public class AppointmentController {
    private final Clinic clinic;
    private final ActivityLog activityLog;
    private Patient currentPatient;
    private Service currentService;
    private Referral currentReferral;
    private final AvailabilityService availabilityService;
    private final AppointmentService appointmentService;

    // forward to clinic
    public List<Patient> findPatient(PatientSearchSpecification spec){
        return clinic.findPatient(spec);
    }

    // set currentPatient
    public void selectPatient(Patient patient){
        this.currentPatient = patient;
    }

    public List<Availability> requestNewAppointment(Service service){
        // check if clinic offers that service
        if(!clinic.offersService(service)){
            throw new ServiceNotOfferedException("Service is not offered at this clinic");
        }
        this.currentService = service;
        this.currentReferral = null;

        if(service.requiresReferral()){
            throw new ReferralRequiredException("This service requires a referral");
        }
        return clinic.searchAvailability(service, null, null);
    }

    // set referral for current service
    public List<Availability> provideReferral(Referral referral){
        if(currentService == null){
            throw new IllegalStateException("Request an appointment first");
        }
        if(referral == null){
            throw new IllegalArgumentException("Referral can't be null");
        }
        if(!this.currentService.requiresReferral()){
            throw new IllegalArgumentException("This service doesn't require a referral");
        }

        this.currentReferral = referral;

        return clinic.searchAvailability(this.currentService, null, null);
    }

    // select availability
    public Appointment select(Availability availability, LocalTime start){
        if(availability == null || start == null){
            throw new IllegalArgumentException("Availability and start time can't be null");
        }
        if(currentPatient == null){
            throw new IllegalStateException("No patient selected");
        }
        if(currentService == null){
            throw new IllegalStateException("No service selected");
        }
        if(currentService.requiresReferral() && currentReferral == null){
            throw new ReferralRequiredException("This service requires a referral");
        }

        Appointment appointment = availability.createAppointment(currentPatient, currentService, currentReferral, clinic, start, currentService.getDurationMinutes());

        if(appointment == null){
            return null; // that time no longer fits
        }

        Availability booked;
        try {
            booked = availabilityService.bookSlot(availability, start, start.plusMinutes(currentService.getDurationMinutes()));
            appointmentService.save(appointment);
        } catch (SQLException | RuntimeException e) {
            availability.getAppointments().remove(appointment); // nothing was saved, so undo
            ConsolePrinter.error("Could not book the appointment: " + e.getMessage());
            return null;
        }
        booked.getAppointments().add(appointment);

        this.currentPatient.addAppointment(appointment);
        this.clinic.addAppointment(appointment);
        this.activityLog.record(currentPatient, appointment);

        this.currentPatient = null;
        this.currentService = null;
        this.currentReferral = null;

        return appointment;
    }

    public List<Appointment> findAppointmentsOfProfessionalForCalendarFrom(int professionalId, LocalDate from) throws SQLException {
        return appointmentService.findAppointmentsOfProfessionalForCalendarFrom(professionalId, from);
    }
    public List<Appointment> findAppointmentsOfPatientForCalendarFrom(int patientId, LocalDate from) throws SQLException{
        return appointmentService.findAppointmentsOfPatientForCalendarFrom(patientId, from);
    }
    public List<Appointment> findAppointmentsOfReferralTypeForCalendarFrom( LocalDate from) throws SQLException {
        return appointmentService.findAppointmentsOfReferralTypeForCalendarFrom( from);
    }
}
