package controllers;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import domain.*;
import lombok.RequiredArgsConstructor;
import services.AvailabilityService;
import utils.ConsolePrinter;

@RequiredArgsConstructor
public class AppointmentController {
    private final Clinic clinic;
    private final ActivityLog activityLog;
    private Patient currentPatient;
    private Service currentService;
    private Referral currentReferral;
    private final AvailabilityService availabilityService;

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

        updateAvailabilityAfterCreateAppointment(availability, start, start.plusMinutes(currentService.getDurationMinutes()));

        this.currentPatient.addAppointment(appointment);
        this.clinic.addAppointment(appointment);
        this.activityLog.record(currentPatient, appointment);

        this.currentPatient = null;
        this.currentService = null;
        this.currentReferral = null;

        return appointment;
    }

    private void updateAvailabilityAfterCreateAppointment(Availability availability, LocalTime appointmentStart, LocalTime appointmentEnd) {
        try{
            availabilityService.removeAvailability(availability);
            availabilityService.createAvailability(availability.getProfessional(), availability.getDate(), appointmentStart, appointmentEnd, true);
            if(!appointmentStart.equals(availability.getStart())){
                availabilityService.createAvailability(availability.getProfessional(), availability.getDate(), availability.getStart(), appointmentStart, false);
            }
            if(!appointmentEnd.equals(availability.getEnd())){
                availabilityService.createAvailability(availability.getProfessional(), availability.getDate(), appointmentEnd, availability.getEnd(), false);
            }
        } catch (SQLException e) {
            ConsolePrinter.error("Issue with updating availabilities: " + e.getMessage());
        }
    }
}
