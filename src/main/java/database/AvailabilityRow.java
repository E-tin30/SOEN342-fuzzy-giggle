package database;

public record AvailabilityRow(int professionalId, String firstName, String lastName, String title,
                              String specialization, String date, String start, String end) {}
