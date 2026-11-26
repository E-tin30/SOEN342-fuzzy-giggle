package services;

import database.DatabaseGateway;
import domain.Patient;
import domain.ResourceType;
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
    private final ResourceService resourceService;

    // fills an empty database with the sample data
    public void seedIfEmpty() throws SQLException, IOException {
        if (db.countServices() == 0) {
            serviceCatalogService.addService("Routine follow-up", "Family Medicine", false, 30);
            serviceCatalogService.addService("Primary care assessment", "Primary Care", false, 30);
            serviceCatalogService.addService("Cardiology consultation", "Cardiology", true, 45);
            serviceCatalogService.addService("Dermatology consultation", "Dermatology", true, 20);
            serviceCatalogService.addService("Orthopedic evaluation", "Orthopedics", true, 45);
        }
        if (db.countResources() == 0) {
            resourceService.addResource("Exam Room 1", ResourceType.ROOM);
            resourceService.addResource("Exam Room 2", ResourceType.ROOM);
            resourceService.addResource("ECG Machine", ResourceType.EQUIPMENT);
            resourceService.addResource("Ultrasound Machine", ResourceType.EQUIPMENT);
            resourceService.addResource("Nurse", ResourceType.ROLE);
            serviceCatalogService.linkResource("Routine follow-up", "Exam Room 1");
            serviceCatalogService.linkResource("Primary care assessment", "Exam Room 1");
            serviceCatalogService.linkResource("Primary care assessment", "Nurse");
            serviceCatalogService.linkResource("Cardiology consultation", "Exam Room 1");
            serviceCatalogService.linkResource("Cardiology consultation", "ECG Machine");
            serviceCatalogService.linkResource("Dermatology consultation", "Exam Room 2");
            serviceCatalogService.linkResource("Orthopedic evaluation", "Exam Room 2");
            serviceCatalogService.linkResource("Orthopedic evaluation", "Nurse");
        }
        if (db.countPatients() == 0) {
            seedPatients();
        }
        if (db.countProfessionals() == 0) {
            Path csv = Path.of("seed-availabilities.csv");
            if (Files.exists(csv)) {
                csvService.importAvailabilitiesFromCSV(csv);
            } else {
                System.out.println("seed-availabilities.csv not found, skipping availability seed.");
            }
        }
    }

    private void seedPatients() throws SQLException {
        patientService.registerPatient(new Patient("Émilie", "Lavoie", LocalDate.parse("1990-04-12"), "514-555-0101", "LAVE90041201", "RAMQ"));
        patientService.registerPatient(new Patient("Olivier", "Côté", LocalDate.parse("1985-09-30"), "514-555-0102", "COTO85093001", "RAMQ"));
        patientService.registerPatient(new Patient("Fatima", "Benali", LocalDate.parse("1978-01-22"), "438-555-0103", "BENF78012201", "RAMQ"));
        patientService.registerPatient(new Patient("Thomas", "Girard", LocalDate.parse("2001-06-05"), "514-555-0104", "GIRT01060501", "RAMQ"));
        patientService.registerPatient(new Patient("Juliette", "Morin", LocalDate.parse("1995-11-18"), "438-555-0105", "MORJ95111801", "RAMQ"));
        patientService.registerPatient(new Patient("Alexandre", "Dubois", LocalDate.parse("1969-03-09"), "514-555-0106", "DUBA69030901", "Manulife"));
        patientService.registerPatient(new Patient("Maya", "Nguyen", LocalDate.parse("1999-07-27"), "438-555-0107", "NGUM99072701", "Blue Cross"));
        patientService.registerPatient(new Patient("Gabriel", "Fortin", LocalDate.parse("1982-12-14"), "514-555-0108", "FORG82121401", "RAMQ"));
    }
}