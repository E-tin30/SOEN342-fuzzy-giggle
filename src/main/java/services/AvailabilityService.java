package services;

import database.AvailabilityRow;
import database.DatabaseGateway;
import domain.Availability;
import domain.DateRange;
import domain.Professional;
import domain.pattern.RepetitionPattern;
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
                    LocalTime.parse(r.end()),
                    r.isBooked()));
        }
        return availabilities;
    }

    public boolean createAvailability(Professional professional, LocalDate date, LocalTime start, LocalTime end, boolean isBooked) throws SQLException {
        if (date == null) {
            throw new IllegalArgumentException("Availability date cannot be null.");
        }
        return createAvailabilities(professional, List.of(date), start, end, isBooked) == 1;
    }

    public int createAvailabilities(Professional professional, Collection<LocalDate> dates, LocalTime start, LocalTime end, boolean isBooked) throws SQLException {
        validateAvailabilityInput(professional, dates, start, end);

        List<Availability> generated = new ArrayList<>();
        for (LocalDate date : dates) {
            if (date == null) {
                throw new IllegalArgumentException("Availability date cannot be null.");
            }
            generated.add(new Availability(professional, date, start, end, isBooked));
        }

        List<Availability> valid = filterConflictingAvailabilities(professional, generated);
        return saveAvailabilities(valid);
    }

    public boolean removeAvailability(Availability availability) throws SQLException {
        return db.removeAvailability(availability);
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
                        availability.getStart(), availability.getEnd(), availability.isBooked())) {
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

    public Availability bookSlot(Availability original, LocalTime bStart, LocalTime bEnd) throws SQLException {
        Professional p = original.getProfessional();
        db.begin();
        try {
            if (!db.removeAvailability(original)) throw new IllegalStateException("Availability not in database");
            insertOrFail(p.getId(), original.getDate(), bStart, bEnd, true);
            if (bStart.isAfter(original.getStart())) insertOrFail(p.getId(), original.getDate(), original.getStart(), bStart, false);
            if (bEnd.isBefore(original.getEnd())) insertOrFail(p.getId(), original.getDate(), bEnd, original.getEnd(), false);
            db.commit();
        } catch (Exception e) {
            db.rollback();
            throw e;
        }
        // mirror the db in memory (see Professional below)
        p.removeAvailability(original);
        Availability booked = new Availability(p, original.getDate(), bStart, bEnd, true);
        p.addAvailability(booked);
        if (bStart.isAfter(original.getStart())) p.addAvailability(new Availability(p, original.getDate(), original.getStart(), bStart, false));
        if (bEnd.isBefore(original.getEnd())) p.addAvailability(new Availability(p, original.getDate(), bEnd, original.getEnd(), false));
        return booked;
    }

    private void insertOrFail(int profId, LocalDate d, LocalTime s, LocalTime e, boolean isBooked) throws SQLException {
        if (!db.addAvailability(profId, d, s, e, isBooked)) throw new IllegalStateException("Could not save " + d + " " + s + "-" + e);
    }

}
