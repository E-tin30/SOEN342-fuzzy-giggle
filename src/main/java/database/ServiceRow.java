package database;

public record ServiceRow(String name, String specialization, boolean requiresReferral, int durationMinutes) {}