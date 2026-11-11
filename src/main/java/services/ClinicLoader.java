package services;

import domain.Availability;
import domain.Clinic;
import domain.Professional;
import domain.Service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Copies the availabilities stored in the database into the Clinic (the in-memory model).
 * Safe to call as many times as you like: slots that are already in the clinic are skipped,
 * so slots that were booked stay booked.
 */
public class ClinicLoader {
    private final Clinic clinic;
    private final AvailabilityService availabilityService;

    // slots already copied into the clinic (professional id | date | start | end)
    private final Set<String> loaded = new HashSet<>();

    // the service each specialization provides
    private final Map<String, Service> servicesBySpecialization = new HashMap<>();

    public ClinicLoader(Clinic clinic, AvailabilityService availabilityService) {
        this.clinic = clinic;
        this.availabilityService = availabilityService;

        register("Family Medicine", new Service("Routine follow-up", false));
        register("Primary Care", new Service("Primary care assessment", false));
        register("Cardiology", new Service("Cardiology consultation", true));
        register("Dermatology", new Service("Dermatology consultation", true));
        register("Orthopedics", new Service("Orthopedic evaluation", true));
    }

    private void register(String specialization, Service service) {
        servicesBySpecialization.put(specialization, service);
        if (!clinic.offersService(service)) {
            clinic.addService(service);
        }
    }

    // a specialization we don't know gets a plain consultation service (no referral needed)
    private Service serviceFor(String specialization) {
        Service service = servicesBySpecialization.get(specialization);
        if (service == null) {
            service = new Service(specialization + " consultation", false);
            register(specialization, service);
        }
        return service;
    }

    private Professional findProfessional(int id) {
        for (Professional p : clinic.getProfessionals()) {
            if (p.getId() == id) return p;
        }
        return null;
    }

    public void sync() throws SQLException {
        for (Availability fromDb : availabilityService.getAllAvailabilities()) {
            Professional dbProfessional = fromDb.getProfessional();

            Professional professional = findProfessional(dbProfessional.getId());
            if (professional == null) {
                professional = dbProfessional;
                professional.addService(serviceFor(professional.getSpecialization()));
                clinic.addProfessional(professional);
            }

            String key = professional.getId() + "|" + fromDb.getDate() + "|"
                    + fromDb.getStart() + "|" + fromDb.getEnd();
            if (loaded.add(key)) {
                professional.addAvailability(new Availability(professional, fromDb.getDate(),
                        fromDb.getStart(), fromDb.getEnd()));
            }
        }
    }
}