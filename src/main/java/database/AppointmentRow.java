package database;

public record AppointmentRow(int id, int professionalId, int patientId, String serviceName,
                             Integer referralId, Integer clinicId, String date,
                             String startTime, String endTime, String status) {}