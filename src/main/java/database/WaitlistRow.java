package database;

public record WaitlistRow(int id, int patientId, Integer professionalId, String specialization,
                            String dateFrom, String dateTo, int priority, String status) {}