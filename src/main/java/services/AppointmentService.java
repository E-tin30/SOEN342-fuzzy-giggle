package services;

import database.CalendarAppointment;
import database.DatabaseGateway;
import domain.Appointment;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

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

    public List<CalendarAppointment> findAppointmentsOfProfessionalForCalendarFrom(int professionalId, LocalDate from) throws SQLException {
        db.begin();
        try{
            return db.findAppointmentsOfProfessionalForCalendarFrom(professionalId, from);
        } catch (SQLException e) {
            db.rollback();
            throw e;
        }
    }

    public List<CalendarAppointment> findAppointmentsOfPatientForCalendarFrom(int patientId, LocalDate from) throws SQLException{
        db.begin();
        try{
            return db.findAppointmentsOfPatientForCalendarFrom(patientId, from);
        } catch (SQLException e) {
            db.rollback();
            throw e;
        }
    }

    public List<CalendarAppointment> findAppointmentsOfReferralTypeForCalendarFrom(LocalDate from) throws SQLException {
        //TODO: add referralType to params
        db.begin();
        try{
            return db.findAppointmentsOfReferralTypeForCalendarFrom( from);
        } catch (SQLException e) {
            db.rollback();
            throw e;
        }
    }
}