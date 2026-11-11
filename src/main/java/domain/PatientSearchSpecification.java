package domain;

import java.util.Date;

public record PatientSearchSpecification(String ramq, Date dateOfBirth,
                                         String phoneNumber, String firstName, String lastName) {}