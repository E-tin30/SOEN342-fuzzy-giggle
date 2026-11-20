package domain;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class Clinic {
    private final String name;
    private final String location;
    private final List<Service> offeredServices = new ArrayList<>();
    private final List<Professional> professionals = new ArrayList<>();
    private final List<Patient> patients = new ArrayList<>();
    private final List<Appointment> appointments = new ArrayList<>();

    // add patient to clinic
    public void addPatient(Patient patient){
        if(patient == null){
            throw new IllegalArgumentException("Patient can't be null");
        }
        this.patients.add(patient);
    }

    // add professional to clinic
    public void addProfessional(Professional professional){
        if(professional == null){
            throw new IllegalArgumentException("Professional can't be null");
        }
        this.professionals.add(professional);
    }

    // add service to clinic
    public void addService(Service service){
        if(service == null){
            throw new IllegalArgumentException("Service can't be null");
        }
        this.offeredServices.add(service);
    }

    // record an appointment booked at this clinic
    public void addAppointment(Appointment appointment){
        if(appointment == null){
            throw new IllegalArgumentException("Appointment can't be null");
        }
        this.appointments.add(appointment);
    }

    // find patient by insurance number, date of birth, phone number, or partial first and/or last name
    public List<Patient> findPatient(PatientSearchSpecification spec) {
        List<Patient> results = new ArrayList<>();
        if (spec == null) return results;
        for (Patient p : patients) {
            if (matches(p, spec)) results.add(p);
        }
        return results;
    }

    private boolean matches(Patient p, PatientSearchSpecification s) {
        boolean anyCriterion = false;

        if (s.ramq() != null) {
            anyCriterion = true;
            if (!s.ramq().equals(p.getRamq())) return false;
        }
        if (s.phoneNumber() != null) {
            anyCriterion = true;
            if (!s.phoneNumber().equals(p.getPhoneNumber())) return false;
        }
        if (s.dateOfBirth() != null) {
            anyCriterion = true;
            if (!s.dateOfBirth().equals(p.getDateOfBirth())) return false;
        }
        if (s.firstName() != null) {
            anyCriterion = true;
            if (!p.getFirstName().toLowerCase().contains(s.firstName().toLowerCase())) return false;
        }
        if (s.lastName() != null) {
            anyCriterion = true;
            if (!p.getLastName().toLowerCase().contains(s.lastName().toLowerCase())) return false;
        }
        return anyCriterion;
    }

    public List<Availability> searchAvailability(Service service, Professional professional, DateRange range) {
        int minutes = (service == null) ? 1 : service.getDurationMinutes(); // no service = any free time
        List<Availability> results = new ArrayList<>();
        for (Professional p : professionals) {
            if (professional != null && !p.equals(professional)) continue; // wrong professional
            if (service != null && !p.offersService(service)) continue; // doesn't offer service
            results.addAll(p.getAvailabilities(range, minutes)); // null range = no filter
        }
        return results;
    }

    public boolean offersService(Service service){
        if(service == null){
            throw new IllegalArgumentException("Service can't be null");
        }
        return this.offeredServices.contains(service);
    }
}
