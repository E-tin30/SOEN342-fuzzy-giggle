package controllers;

import domain.Clinic;
import domain.Patient;
import lombok.RequiredArgsConstructor;
import services.PatientService;

import java.sql.SQLException;
import java.time.LocalDate;

@RequiredArgsConstructor
public class PatientController {
    private final Clinic clinic;
    private final PatientService patientService;

    // returns false if a patient with this RAMQ already exists
    public boolean registerPatient(String first, String last, LocalDate dob, String phone, String ramq, String insurance) throws SQLException {
        Patient patient = new Patient(first, last, dob, phone, ramq, insurance);
        if (!patientService.registerPatient(patient)) {
            return false;
        }
        clinic.addPatient(patient); //so it can be found and booked right away, without a restart
        return true;
    }
}