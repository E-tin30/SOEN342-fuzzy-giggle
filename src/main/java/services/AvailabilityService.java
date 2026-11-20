package services;

import database.AvailabilityRow;
import database.DatabaseGateway;
import domain.Availability;
import domain.DateRange;
import domain.Professional;
import domain.RepetitionPattern;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
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
                    LocalDate.parse(r.date()),
                    LocalTime.parse(r.start()),
                    LocalTime.parse(r.end())));
        }
        return availabilities;
    }

    public boolean createAvailability(Professional professional, LocalDate date, LocalTime start, LocalTime end) throws SQLException {
        if (date == null) {
            throw new IllegalArgumentException("Availability date cannot be null.");
        }
        return createAvailabilities(professional, List.of(date), start, end) == 1;
    }

    public int createAvailabilities(Professional professional, Collection<LocalDate> dates, LocalTime start, LocalTime end) throws SQLException {
        validateAvailabilityInput(professional, dates, start, end);

        List<Availability> generated = new ArrayList<>();
        for (LocalDate date : dates) {
            if (date == null) {
                throw new IllegalArgumentException("Availability date cannot be null.");
            }
            generated.add(new Availability(professional, date, start, end));
        }

        List<Availability> valid = filterConflictingAvailabilities(professional, generated);
        return saveAvailabilities(valid);
    }


    public List<Availability> generateAvailabilities(Professional professional, DateRange range, RepetitionPattern pattern, LocalTime start, LocalTime end) {
        if (professional == null || range == null || pattern == null || start == null || end == null) {
            throw new IllegalArgumentException("Missing availability information.");
        }

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        List<Availability> availabilities = new ArrayList<>();

        for (LocalDate date : pattern.generateDates(range.from(), range.to())) {
            Availability availability = new Availability(professional, date, start, end);
            availabilities.add(availability);
        }

        return availabilities;
    }


    public List<Availability> filterConflictingAvailabilities(Professional professional, List<Availability> generated) {
        if (professional == null || generated == null) {
            throw new IllegalArgumentException("Professional and availabilities cannot be null.");
        }
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

    public int saveAvailabilities(Collection<Availability> availabilities) throws SQLException {
        if (availabilities == null) {
            throw new IllegalArgumentException("Availabilities cannot be null.");
        }
        if (availabilities.isEmpty()) {
            return 0;
        }

        List<Availability> saved = new ArrayList<>();
        db.begin();
        try {
            for (Availability availability : availabilities) {
                if (availability == null || availability.getProfessional() == null
                        || availability.getDate() == null || availability.getStart() == null
                        || availability.getEnd() == null) {
                    throw new IllegalArgumentException("Availability information cannot be null.");
                }

                Professional professional = availability.getProfessional();
                int professionalId = db.findOrCreateProfessional(
                        professional.getFirstName(), professional.getLastName(),
                        professional.getTitle(), professional.getSpecialization());

                if (db.addAvailability(professionalId, availability.getDate(),
                        availability.getStart(), availability.getEnd())) {
                    saved.add(availability);
                }
            }
            db.commit();
        } catch (Exception e) {
            db.rollback();
            throw e;
        }

        for (Availability availability : saved) {
            availability.getProfessional().addAvailability(availability);
        }
        return saved.size();
    }

    private static void validateAvailabilityInput(Professional professional,
                                                  Collection<LocalDate> dates,
                                                  LocalTime start, LocalTime end) {
        if (professional == null || dates == null || start == null || end == null) {
            throw new IllegalArgumentException("Missing availability information.");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
    }

}
