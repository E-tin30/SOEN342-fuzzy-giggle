package domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@AllArgsConstructor
@Getter @Setter
public class Patient {
    private String firstName;
    private String lastName;
    private Date dateOfBirth;
    private String phoneNumber;
    private String ramq;
    private String insurance;
    private final List<Appointment> appointments = new ArrayList<>();

    public void addAppointment(Appointment appointment){
        if(appointment == null){
            throw new IllegalArgumentException("Appointment can't be null");
        }
        appointments.add(appointment);
    }
}
