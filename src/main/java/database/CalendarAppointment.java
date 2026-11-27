package database;

import java.time.LocalDateTime;

//exposes only necessary information for exporting to calendar feature, ignoring sensitive information
public record CalendarAppointment(int id, LocalDateTime start, LocalDateTime end, String professional) {}
