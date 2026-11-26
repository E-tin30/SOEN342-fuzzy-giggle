package services;

import database.DatabaseGateway;
import database.PatientRow;
import domain.Patient;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class PatientService {
    private final DatabaseGateway db;

    // returns false if a patient with this RAMQ already exists
    public boolean registerPatient(Patient patient) throws SQLException {
        if (patient == null || patient.getFirstName() == null || patient.getLastName() == null
                || patient.getDateOfBirth() == null || patient.getPhoneNumber() == null
                || patient.getRamq() == null || patient.getInsurance() == null) {
            throw new IllegalArgumentException("Patient information cannot be null.");
        }
        int id = db.addPatient(patient.getFirstName(), patient.getLastName(), patient.getDateOfBirth(),
            patient.getPhoneNumber(), patient.getRamq(), patient.getInsurance());
        if (id < 0) return false;
        patient.setId(id);
        return true;
    }

    public List<Patient> getAllPatients() throws SQLException {
        List<Patient> patients = new ArrayList<>();
        for (PatientRow r : db.findAllPatients()) {
            Patient patient = new Patient(r.firstName(), r.lastName(), LocalDate.parse(r.dateOfBirth()),
                    r.phoneNumber(), r.ramq(), r.insurance());
            patient.setId(r.id());
            patients.add(patient);
        }
        return patients;
    }
}