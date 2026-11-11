package database;

public record AvailabilityRow(int professionalId, String first_name, String last_name, String title,
                              String specialization, String date, String start, String end) {}
