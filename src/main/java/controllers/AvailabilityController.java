package controllers;

import java.util.List;

import domain.*;
import lombok.AllArgsConstructor;

@AllArgsConstructor 
public class AvailabilityController {
    private final Clinic clinic;

    public List<Availability> searchAvailability(Service service, Professional professional, DateRange range){
        return this.clinic.searchAvailability(service, professional, range);
    }
}
