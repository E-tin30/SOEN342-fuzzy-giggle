package services;

import database.DatabaseGateway;
import domain.Appointment;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;

@RequiredArgsConstructor
public class AppointmentService {
    private final DatabaseGateway db;

    //saves appointment to db
    public void save(Appointment a) throws SQLException {
        db.begin();
        try{
            db.addAppointment(a.getProfessional().getId(), a.getPatient().getId(), a.getService().getName(), a.getReferral().getId(), a.getClinic().getId(), a.getDate(), a.getStart(), a.getEnd(), a.getStatus().toString());
            db.commit();
        } catch (SQLException e) {
            db.rollback();
            throw e;
        }

    }
}