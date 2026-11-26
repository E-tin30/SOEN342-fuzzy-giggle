package controllers;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import domain.Availability;
import domain.Clinic;
import domain.DateRange;
import domain.Professional;
import domain.RepetitionPattern;
import domain.Service;
import lombok.AllArgsConstructor;
import services.AvailabilityService;

@AllArgsConstructor 
public class AvailabilityController {
    private final Clinic clinic;
    private final AvailabilityService availabilityService;


    public List<Availability> searchAvailability(Service service, Professional professional, DateRange range){
        return this.clinic.searchAvailability(service, professional, range);
    }

    public boolean createSingleAvailability(Professional professional, LocalDate date, LocalTime start, LocalTime end, boolean isBooked) throws SQLException {
        return availabilityService.createAvailability(professional, date, start, end, isBooked);
    }

    public int createRecurringAvailability(Professional professional, DateRange range, RepetitionPattern pattern, LocalTime start, LocalTime end) throws SQLException {
        List<Availability> generated = availabilityService.generateAvailabilities(professional, range, pattern, start, end);
        List<Availability> valid = availabilityService.filterConflictingAvailabilities(professional, generated);
        return availabilityService.saveAvailabilities(valid);
    }
}
