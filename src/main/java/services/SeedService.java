package services;

import database.DatabaseGateway;
import domain.Patient;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;

@RequiredArgsConstructor
public class SeedService {
    private final DatabaseGateway db;
    private final PatientService patientService;
    private final CSVService csvService;
    private final ServiceCatalogService serviceCatalogService;

    // fills an empty database with the sample data
    public void seedIfEmpty() throws SQLException, IOException {
        if (db.countServices() == 0) {
            serviceCatalogService.addService("Routine follow-up", "Family Medicine", false, 30);
            serviceCatalogService.addService("Primary care assessment", "Primary Care", false, 30);
            serviceCatalogService.addService("Cardiology consultation", "Cardiology", true, 45);
            serviceCatalogService.addService("Dermatology consultation", "Dermatology", true, 20);
            serviceCatalogService.addService("Orthopedic evaluation", "Orthopedics", true, 45);
        }
        if (db.countPatients() == 0) {
            seedPatients();
        }
        if (db.countProfessionals() == 0) {
            Path csv = Path.of("sample-availabilities.csv");
            if (Files.exists(csv)) {
                csvService.importAvailabilitiesFromCSV(csv);
            } else {
                System.out.println("sample-availabilities.csv not found, skipping availability seed.");
            }
        }
    }

    private void seedPatients() throws SQLException {
        // paste the 8 patients from main() here, one line each:
        patientService.registerPatient(new Patient("Émilie", "Lavoie", LocalDate.parse("1990-04-12"), "514-555-0101", "LAVE90041201", "RAMQ"));
        // ... the other 7
    }
}