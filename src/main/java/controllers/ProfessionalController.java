package controllers;

import lombok.RequiredArgsConstructor;
import services.ProfessionalService;
import services.ServiceCatalogService;

import java.sql.SQLException;
import java.util.List;

@RequiredArgsConstructor
public class ProfessionalController {
    private final ProfessionalService professionalService;
    private final ServiceCatalogService serviceCatalogService;

    public boolean registerProfessional(String first, String last, String title, String specialization, List<String> serviceNames) throws SQLException {
        return professionalService.registerProfessional(first, last, title, specialization, serviceNames);
    }

    // returns false if a service with this name already exists
    public boolean addService(String name, String specialization, boolean requiresReferral, int minutes, List<String> resourceNames) throws SQLException {
        boolean added = serviceCatalogService.addService(name, specialization, requiresReferral, minutes);
        if (added) {
            for (String resourceName : resourceNames) {
                serviceCatalogService.linkResource(name, resourceName);
            }
        }
        return added;
    }
}