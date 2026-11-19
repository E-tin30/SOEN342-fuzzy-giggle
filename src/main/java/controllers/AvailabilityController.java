package controllers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import domain.*;
import lombok.AllArgsConstructor;

@AllArgsConstructor 
public class AvailabilityController {
    private final Clinic clinic;

    public List<Availability> searchAvailability(Service service, Professional professional, DateRange range){
        return this.clinic.searchAvailability(service, professional, range);
    }

    public void createSingleAvailability(Professional professional, LocalDate date, LocalTime start, LocalTime end){

    };

    public void createRecurringAvailability(Professional professional, DateRange range, RepetitionPattern pattern, LocalTime start, LocalTime end){
        
    };
}
