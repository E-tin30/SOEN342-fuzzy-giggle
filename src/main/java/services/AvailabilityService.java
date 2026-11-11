package services;

import database.AvailabilityRow;
import database.DatabaseGateway;
import domain.Availability;
import domain.Professional;
import lombok.RequiredArgsConstructor;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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
                    id -> new Professional(id, r.first_name(), r.last_name(), r.title(), r.specialization()));

            availabilities.add(new Availability(
                    professional,
                    Date.valueOf(LocalDate.parse(r.date())),
                    Time.valueOf(LocalTime.parse(r.start())),
                    Time.valueOf(LocalTime.parse(r.end()))));
        }
        return availabilities;
    }

}
