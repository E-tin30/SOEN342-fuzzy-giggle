package services;

import domain.Availability;
import domain.Clinic;
import domain.Professional;
import domain.Service;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Copies the professionals, services and availabilities stored in the database into the Clinic (the in-memory model).
 * Safe to call as many times as you like: slots that are already in the clinic are skipped,
 * so slots that were booked stay booked.
 */
public class ClinicLoader {
    private final Clinic clinic;
    private final AvailabilityService availabilityService;
    private final ProfessionalService professionalService;
    private final ServiceCatalogService serviceCatalogService;

    // slots already copied into the clinic (professional id | date | start | end)
    private final Set<String> loaded = new HashSet<>();

    public ClinicLoader(Clinic clinic, AvailabilityService availabilityService,
                        ProfessionalService professionalService, ServiceCatalogService serviceCatalogService) {
        this.clinic = clinic;
        this.availabilityService = availabilityService;
        this.professionalService = professionalService;
        this.serviceCatalogService = serviceCatalogService;
    }

    private Professional findProfessional(int id) {
        for (Professional p : clinic.getProfessionals()) {
            if (p.getId() == id) return p;
        }
        return null;
    }

    // every service of the catalog is offered by the clinic
    private void loadServices() throws SQLException {
        for (Service service : serviceCatalogService.getAllServices()) {
            if (!clinic.offersService(service)) {
                clinic.addService(service);
            }
        }
    }

    // puts the professional in the clinic with the services saved for them
    private void addToClinic(Professional professional) throws SQLException {
        for (Service service : serviceCatalogService.ensureDefaultServices(professional)) {
            if (!clinic.offersService(service)) {
                clinic.addService(service);
            }
            if (!professional.offersService(service)) {
                professional.addService(service);
            }
        }
        clinic.addProfessional(professional);
    }

    public void sync() throws SQLException {
        loadServices();

        for (Professional fromDb : professionalService.getAllProfessionals()) {
            if (findProfessional(fromDb.getId()) == null) {
                addToClinic(fromDb);
            }
        }

        for (Availability fromDb : availabilityService.getAllAvailabilities()) {
            Professional dbProfessional = fromDb.getProfessional();

            Professional professional = findProfessional(dbProfessional.getId());
            if (professional == null) {
                professional = dbProfessional;
                addToClinic(professional);
            }

            String key = professional.getId() + "|" + fromDb.getDate() + "|"
                    + fromDb.getStart() + "|" + fromDb.getEnd();
            if (loaded.add(key)) {
                boolean alreadyPresent = professional.getAvailabilities().stream().anyMatch(existing ->
                        existing.getDate().equals(fromDb.getDate())
                                && existing.getStart().equals(fromDb.getStart())
                                && existing.getEnd().equals(fromDb.getEnd()));
                if (!alreadyPresent) {
                    professional.addAvailability(new Availability(professional, fromDb.getDate(),
                            fromDb.getStart(), fromDb.getEnd(), fromDb.isBooked()));
                }
            }
        }
    }
}