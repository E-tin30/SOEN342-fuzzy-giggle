package services;

import database.DatabaseGateway;
import database.ServiceRow;
import domain.Professional;
import domain.Service;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class ServiceCatalogService {
    private final DatabaseGateway db;

    public boolean addService(String name, String specialization, boolean requiresReferral, int minutes) throws SQLException {
        if (name == null || name.isBlank() || specialization == null || specialization.isBlank()) {
            throw new IllegalArgumentException("Service name and specialization are required.");
        }
        if (minutes <= 0) {
            throw new IllegalArgumentException("Duration must be positive.");
        }
        return db.addService(name, specialization, requiresReferral, minutes);
    }

    public List<Service> getAllServices() throws SQLException {
        return toServices(db.findAllServices());
    }

    public List<Service> getServicesOf(Professional professional) throws SQLException {
        return toServices(db.findServicesOfProfessional(professional.getId()));
    }

    // a professional without services gets the ones tagged with their specialization;
    // if there are none, a plain consultation service is created for that specialization
    public List<Service> ensureDefaultServices(Professional professional) throws SQLException {
        List<Service> services = getServicesOf(professional);
        if (services.isEmpty()) {
            db.linkProfessionalToSpecialization(professional.getId(), professional.getSpecialization());
            services = getServicesOf(professional);
        }
        if (services.isEmpty()) {
            Service plain = createDefaultService(professional.getSpecialization());
            db.linkProfessionalToService(professional.getId(), plain.getName());
            services = getServicesOf(professional);
        }
        return services;
    }

    // plain consultation: no referral, 30 min
    private Service createDefaultService(String specialization) throws SQLException {
        Service service = new Service(specialization + " consultation", false, 30);
        db.addService(service.getName(), specialization, false, 30);
        return service;
    }

    private List<Service> toServices(List<ServiceRow> rows) {
        List<Service> services = new ArrayList<>();
        for (ServiceRow r : rows) {
            services.add(new Service(r.name(), r.requiresReferral(), r.durationMinutes()));
        }
        return services;
    }
}