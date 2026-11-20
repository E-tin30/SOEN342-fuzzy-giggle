package services;

import database.AvailabilityRow;
import database.DatabaseGateway;
import domain.Availability;
import domain.DateRange;
import domain.Professional;
import domain.RepetitionPattern;
import lombok.RequiredArgsConstructor;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class AvailabilityService {
    private final DatabaseGateway db;

    public List<Availability> getAllAvailabilities() throws SQLException {
        //retrieve from database
        List<AvailabilityRow> rows = db.findAllAvailabilities();

        //create objects
        Map<Integer, Professional> professionals = new HashMap<>();
        List<Availability> availabilities = new ArrayList<>();

        for (AvailabilityRow r : rows) {
            Professional professional = professionals.computeIfAbsent(r.professionalId(),
                    id -> new Professional(id, r.firstName(), r.lastName(), r.title(), r.specialization()));

            availabilities.add(new Availability(
                    professional,
                    Date.valueOf(LocalDate.parse(r.date())),
                    Time.valueOf(LocalTime.parse(r.start())),
                    Time.valueOf(LocalTime.parse(r.end()))));
        }
        return availabilities;
    }

    public boolean createAvailability(Professional professional, LocalDate date, LocalTime start, LocalTime end){

    };

    public int createAvailabilities(Professional professional, Collection<LocalDate> dates, LocalTime start,LocalTime end){

    };


    public List<Availability> generateAvailabilities(Professional professional, DateRange range, RepetitionPattern pattern, LocalTime start, LocalTime end) {
        if (professional == null || range == null || pattern == null || start == null || end == null || range.from() == null || range.to() == null) {
            throw new IllegalArgumentException("Missing availability information.");
        }

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        LocalDate from = new Date(range.from().getTime()).toLocalDate();
        LocalDate to = new Date(range.to().getTime()).toLocalDate();

        List<Availability> availabilities = new ArrayList<>();

        for (LocalDate date : pattern.generateDates(from, to)) {
            Availability availability = new Availability(professional, Date.valueOf(date), Time.valueOf(start),Time.valueOf(end));
            availabilities.add(availability);
        }

        return availabilities;
    }


    public List<Availability> filterConflictingAvailabilities(Professional professional, List<Availability> generated) {
        List<Availability> valid = new ArrayList<>();

        //Include existing availabilities when checking conflicts
        List<Availability> occupied = new ArrayList<>(professional.getAvailabilities());

        for (Availability candidate : generated) {
            boolean conflict = false;

            for (Availability existing : occupied) {
                if (candidate.overlaps(existing)) {
                    conflict = true;
                    break;
                }
            }

            if (!conflict) {
                valid.add(candidate);
                occupied.add(candidate);
            }
        }
        return valid;
    }


}
