package domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class Patient {
    private int id; // set by db
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String phoneNumber;
    private String ramq;
    private String insurance;
    private final List<Appointment> appointments = new ArrayList<>();

    public Patient(String firstName, String lastName, LocalDate dateOfBirth, String phoneNumber, String ramq, String insurance) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.phoneNumber = phoneNumber;
        this.ramq = ramq;
        this.insurance = insurance;
    }

    public void addAppointment(Appointment appointment){
        if(appointment == null){
            throw new IllegalArgumentException("Appointment can't be null");
        }
        appointments.add(appointment);
    }
}
