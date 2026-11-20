package domain;

import java.time.LocalDate;

public record PatientSearchSpecification(String ramq, LocalDate dateOfBirth, String phoneNumber, String firstName, String lastName) {
    
}
