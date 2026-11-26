package database;

import domain.Availability;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//This class is the only one to have SQL statements. It controls the access to the database
public class DatabaseGateway implements AutoCloseable {

    // ---- simple row holders for the new tables (move to their own files if you prefer) ----
    public record ServiceRow(int id, String name, boolean requiresReferral, int durationMinutes) {}

    public record PatientRow(int id, String firstName, String lastName, String dateOfBirth,
                             String phoneNumber, String ramq, String insurance) {}

    public record AppointmentRow(int id, int professionalId, int patientId, int clinicId, int serviceId,
                                 Integer referralId, String date, String startTime, String endTime,
                                 String status) {}

    private final Connection conn;
    // every prepared statement is registered here so close() can never forget one
    private final List<PreparedStatement> statements = new ArrayList<>();

    // professional / availability
    private final PreparedStatement findProfessional;
    private final PreparedStatement insertProfessional;
    private final PreparedStatement insertAvailability;
    private final PreparedStatement removeAvailability;
    private final PreparedStatement selectAllAvailabilities;
    private final PreparedStatement markAvailabilityBooked;

    // clinic
    private final PreparedStatement findClinic;
    private final PreparedStatement insertClinic;
    private final PreparedStatement linkClinicProfessional;
    private final PreparedStatement linkClinicService;

    // service
    private final PreparedStatement findService;
    private final PreparedStatement insertService;
    private final PreparedStatement linkProfessionalService;
    private final PreparedStatement selectAllServices;

    // patient
    private final PreparedStatement findPatientByRamq;
    private final PreparedStatement insertPatient;
    private final PreparedStatement updatePatient;
    private final PreparedStatement selectAllPatients;

    // referral
    private final PreparedStatement insertReferral;
    private final PreparedStatement updateReferralStatus;

    // appointment
    private final PreparedStatement insertAppointment;
    private final PreparedStatement deleteAppointment;
    private final PreparedStatement updateAppointmentStatus;
    private final PreparedStatement selectAllAppointments;

    private final Map<List<String>, Integer> professionalCache = new HashMap<>();
    private final Map<String, Integer> serviceCache = new HashMap<>();
    private final Map<List<String>, Integer> clinicCache = new HashMap<>();
    private final Map<String, Integer> patientCache = new HashMap<>(); // key = ramq

    public DatabaseGateway(String url) throws SQLException {
        conn = DriverManager.getConnection(url);
        try {
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS professional (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            first_name TEXT NOT NULL,
                            last_name TEXT NOT NULL,
                            title TEXT NOT NULL,
                            specialization TEXT NOT NULL,
                            UNIQUE(first_name, last_name, title, specialization)
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS availability (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            date TEXT NOT NULL,
                            start_time TEXT NOT NULL,
                            end_time TEXT NOT NULL,
                            is_booked INTEGER NOT NULL DEFAULT 0,
                            professional_id INTEGER NOT NULL REFERENCES professional(id),
                            UNIQUE(professional_id, date, start_time)
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS clinic (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL,
                            location TEXT NOT NULL,
                            UNIQUE(name, location)
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS service (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL UNIQUE,
                            requires_referral INTEGER NOT NULL DEFAULT 0,
                            duration_minutes INTEGER NOT NULL CHECK(duration_minutes > 0)
                        )""");
                // many-to-many: which services a professional offers
                st.execute("""
                        CREATE TABLE IF NOT EXISTS professional_service (
                            professional_id INTEGER NOT NULL REFERENCES professional(id),
                            service_id INTEGER NOT NULL REFERENCES service(id),
                            PRIMARY KEY(professional_id, service_id)
                        )""");
                // many-to-many: which services a clinic offers
                st.execute("""
                        CREATE TABLE IF NOT EXISTS clinic_service (
                            clinic_id INTEGER NOT NULL REFERENCES clinic(id),
                            service_id INTEGER NOT NULL REFERENCES service(id),
                            PRIMARY KEY(clinic_id, service_id)
                        )""");
                // many-to-many: which professionals work at a clinic
                st.execute("""
                        CREATE TABLE IF NOT EXISTS clinic_professional (
                            clinic_id INTEGER NOT NULL REFERENCES clinic(id),
                            professional_id INTEGER NOT NULL REFERENCES professional(id),
                            PRIMARY KEY(clinic_id, professional_id)
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS patient (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            first_name TEXT NOT NULL,
                            last_name TEXT NOT NULL,
                            date_of_birth TEXT NOT NULL,
                            phone_number TEXT NOT NULL,
                            ramq TEXT NOT NULL UNIQUE,
                            insurance TEXT
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS referral (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            patient_id INTEGER NOT NULL REFERENCES patient(id),
                            referring_physician TEXT NOT NULL,
                            referral_type TEXT NOT NULL,
                            requested_specialty TEXT,
                            requested_professional TEXT,
                            clinical_reason TEXT NOT NULL,
                            priority TEXT NOT NULL,
                            status TEXT NOT NULL
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS appointment (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            professional_id INTEGER NOT NULL REFERENCES professional(id),
                            patient_id INTEGER NOT NULL REFERENCES patient(id),
                            clinic_id INTEGER NOT NULL REFERENCES clinic(id),
                            service_id INTEGER NOT NULL REFERENCES service(id),
                            referral_id INTEGER REFERENCES referral(id),
                            date TEXT NOT NULL,
                            start_time TEXT NOT NULL,
                            end_time TEXT NOT NULL,
                            status TEXT NOT NULL DEFAULT 'PENDING',
                            UNIQUE(professional_id, date, start_time)
                        )""");
            }

            // ---- professional / availability ----
            findProfessional = prepare(
                    "SELECT id FROM professional "
                            + "WHERE first_name=? AND last_name=? AND title=? AND specialization=?");
            insertProfessional = prepare(
                    "INSERT INTO professional(first_name, last_name, title, specialization) VALUES(?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            insertAvailability = prepare(
                    "INSERT OR IGNORE INTO availability(date, start_time, end_time, professional_id, is_booked) "
                            + "VALUES(?,?,?,?,?)");
            removeAvailability = prepare(
                    "DELETE FROM availability WHERE professional_id = ? AND date = ? AND start_time = ?");
            selectAllAvailabilities = prepare("""
                    SELECT p.id, p.first_name, p.last_name, p.title, p.specialization,
                           a.date, a.start_time, a.end_time, a.is_booked
                    FROM availability a
                    JOIN professional p ON p.id = a.professional_id
                    ORDER BY p.last_name, p.first_name, a.date, a.start_time
                    """);
            markAvailabilityBooked = prepare(
                    "UPDATE availability SET is_booked = ? WHERE professional_id = ? AND date = ? AND start_time = ?");

            // ---- clinic ----
            findClinic = prepare("SELECT id FROM clinic WHERE name=? AND location=?");
            insertClinic = prepare("INSERT INTO clinic(name, location) VALUES(?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            linkClinicProfessional = prepare(
                    "INSERT OR IGNORE INTO clinic_professional(clinic_id, professional_id) VALUES(?,?)");
            linkClinicService = prepare(
                    "INSERT OR IGNORE INTO clinic_service(clinic_id, service_id) VALUES(?,?)");

            // ---- service ----
            findService = prepare("SELECT id FROM service WHERE name=?");
            insertService = prepare(
                    "INSERT INTO service(name, requires_referral, duration_minutes) VALUES(?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            linkProfessionalService = prepare(
                    "INSERT OR IGNORE INTO professional_service(professional_id, service_id) VALUES(?,?)");
            selectAllServices = prepare(
                    "SELECT id, name, requires_referral, duration_minutes FROM service ORDER BY name");

            // ---- patient ----
            findPatientByRamq = prepare("SELECT id FROM patient WHERE ramq=?");
            insertPatient = prepare(
                    "INSERT INTO patient(first_name, last_name, date_of_birth, phone_number, ramq, insurance) "
                            + "VALUES(?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            updatePatient = prepare(
                    "UPDATE patient SET first_name=?, last_name=?, date_of_birth=?, phone_number=?, insurance=? "
                            + "WHERE ramq=?");
            selectAllPatients = prepare(
                    "SELECT id, first_name, last_name, date_of_birth, phone_number, ramq, insurance "
                            + "FROM patient ORDER BY last_name, first_name");

            // ---- referral ----
            insertReferral = prepare(
                    "INSERT INTO referral(patient_id, referring_physician, referral_type, requested_specialty, "
                            + "requested_professional, clinical_reason, priority, status) VALUES(?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            updateReferralStatus = prepare("UPDATE referral SET status=? WHERE id=?");

            // ---- appointment ----
            insertAppointment = prepare(
                    "INSERT INTO appointment(professional_id, patient_id, clinic_id, service_id, referral_id, "
                            + "date, start_time, end_time, status) VALUES(?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            deleteAppointment = prepare(
                    "DELETE FROM appointment WHERE professional_id = ? AND date = ? AND start_time = ?");
            updateAppointmentStatus = prepare(
                    "UPDATE appointment SET status = ? WHERE professional_id = ? AND date = ? AND start_time = ?");
            selectAllAppointments = prepare(
                    "SELECT id, professional_id, patient_id, clinic_id, service_id, referral_id, "
                            + "date, start_time, end_time, status FROM appointment "
                            + "ORDER BY professional_id, date, start_time");
        } catch (SQLException e) {
            closeStatementsQuietly();
            conn.close();
            throw e;
        }
    }

    private PreparedStatement prepare(String sql) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        statements.add(ps);
        return ps;
    }

    private PreparedStatement prepare(String sql, int generatedKeys) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql, generatedKeys);
        statements.add(ps);
        return ps;
    }

    // transactions

    public void begin() throws SQLException {
        conn.setAutoCommit(false);
    }

    public void commit() throws SQLException {
        conn.commit();
        conn.setAutoCommit(true);
    }

    public void rollback() throws SQLException {
        conn.rollback();
        conn.setAutoCommit(true);
        // cached ids may point to rows that were just rolled back
        professionalCache.clear();
        serviceCache.clear();
        clinicCache.clear();
        patientCache.clear();
    }

    // professional

    public int findOrCreateProfessional(String firstName, String lastName,
                                        String title, String specialization) throws SQLException {
        List<String> key = List.of(firstName, lastName, title, specialization);
        Integer cached = professionalCache.get(key);
        if (cached != null) {
            return cached;
        }

        findProfessional.setString(1, firstName);
        findProfessional.setString(2, lastName);
        findProfessional.setString(3, title);
        findProfessional.setString(4, specialization);
        try (ResultSet rs = findProfessional.executeQuery()) {
            if (rs.next()) {
                int id = rs.getInt(1);
                professionalCache.put(key, id);
                return id;
            }
        }

        insertProfessional.setString(1, firstName);
        insertProfessional.setString(2, lastName);
        insertProfessional.setString(3, title);
        insertProfessional.setString(4, specialization);
        insertProfessional.executeUpdate();
        try (ResultSet keys = insertProfessional.getGeneratedKeys()) {
            keys.next();
            int id = keys.getInt(1);
            professionalCache.put(key, id);
            return id;
        }
    }

    // availability

    // adds an availability into the database
    public boolean addAvailability(int professionalId, LocalDate date,
                                   LocalTime start, LocalTime end, boolean isBooked) throws SQLException {
        insertAvailability.setString(1, date.toString());   // yyyy-MM-dd
        insertAvailability.setString(2, start.toString());  // HH:mm
        insertAvailability.setString(3, end.toString());
        insertAvailability.setInt(4, professionalId);
        insertAvailability.setBoolean(5, isBooked);
        return insertAvailability.executeUpdate() > 0;
    }

    //removes availability from database (fails if an appointment still references it)
    public boolean removeAvailability(Availability availability) throws SQLException {
        removeAvailability.setInt(1, availability.getProfessional().getId());
        removeAvailability.setString(2, availability.getDate().toString());
        removeAvailability.setString(3, availability.getStart().toString());
        return removeAvailability.executeUpdate() > 0;
    }

    // availabilities have no id in the domain, so they are identified by (professional, date, start)
    public boolean setAvailabilityBooked(int professionalId, LocalDate date,
                                         LocalTime start, boolean booked) throws SQLException {
        markAvailabilityBooked.setBoolean(1, booked);
        markAvailabilityBooked.setInt(2, professionalId);
        markAvailabilityBooked.setString(3, date.toString());
        markAvailabilityBooked.setString(4, start.toString());
        return markAvailabilityBooked.executeUpdate() > 0;
    }

    // finds and returns all the availabilities from the database
    public List<AvailabilityRow> findAllAvailabilities() throws SQLException {
        List<AvailabilityRow> rows = new ArrayList<>();
        try (ResultSet rs = selectAllAvailabilities.executeQuery()) {
            while (rs.next()) {
                rows.add(new AvailabilityRow(
                        rs.getInt("id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("title"),
                        rs.getString("specialization"),
                        rs.getString("date"),
                        rs.getString("start_time"),
                        rs.getString("end_time"),
                        rs.getBoolean("is_booked")));
            }
        }
        return rows;
    }

    // clinic

    public int findOrCreateClinic(String name, String location) throws SQLException {
        List<String> key = List.of(name, location);
        Integer cached = clinicCache.get(key);
        if (cached != null) {
            return cached;
        }

        findClinic.setString(1, name);
        findClinic.setString(2, location);
        try (ResultSet rs = findClinic.executeQuery()) {
            if (rs.next()) {
                int id = rs.getInt(1);
                clinicCache.put(key, id);
                return id;
            }
        }

        insertClinic.setString(1, name);
        insertClinic.setString(2, location);
        insertClinic.executeUpdate();
        try (ResultSet keys = insertClinic.getGeneratedKeys()) {
            keys.next();
            int id = keys.getInt(1);
            clinicCache.put(key, id);
            return id;
        }
    }

    public void addProfessionalToClinic(int clinicId, int professionalId) throws SQLException {
        linkClinicProfessional.setInt(1, clinicId);
        linkClinicProfessional.setInt(2, professionalId);
        linkClinicProfessional.executeUpdate();
    }

    public void addServiceToClinic(int clinicId, int serviceId) throws SQLException {
        linkClinicService.setInt(1, clinicId);
        linkClinicService.setInt(2, serviceId);
        linkClinicService.executeUpdate();
    }

    // service

    public int findOrCreateService(String name, boolean requiresReferral,
                                   int durationMinutes) throws SQLException {
        Integer cached = serviceCache.get(name);
        if (cached != null) {
            return cached;
        }

        findService.setString(1, name);
        try (ResultSet rs = findService.executeQuery()) {
            if (rs.next()) {
                int id = rs.getInt(1);
                serviceCache.put(name, id);
                return id;
            }
        }

        insertService.setString(1, name);
        insertService.setBoolean(2, requiresReferral);
        insertService.setInt(3, durationMinutes);
        insertService.executeUpdate();
        try (ResultSet keys = insertService.getGeneratedKeys()) {
            keys.next();
            int id = keys.getInt(1);
            serviceCache.put(name, id);
            return id;
        }
    }

    public void addServiceToProfessional(int professionalId, int serviceId) throws SQLException {
        linkProfessionalService.setInt(1, professionalId);
        linkProfessionalService.setInt(2, serviceId);
        linkProfessionalService.executeUpdate();
    }

    public List<ServiceRow> findAllServices() throws SQLException {
        List<ServiceRow> rows = new ArrayList<>();
        try (ResultSet rs = selectAllServices.executeQuery()) {
            while (rs.next()) {
                rows.add(new ServiceRow(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getBoolean("requires_referral"),
                        rs.getInt("duration_minutes")));
            }
        }
        return rows;
    }

    // patient

    // the RAMQ number is the natural key: existing patient -> details are updated, otherwise inserted
    public int saveOrUpdatePatient(String firstName, String lastName, LocalDate dateOfBirth,
                                   String phoneNumber, String ramq, String insurance) throws SQLException {
        Integer id = cachedOrFoundPatientId(ramq);
        if (id != null) {
            updatePatient.setString(1, firstName);
            updatePatient.setString(2, lastName);
            updatePatient.setString(3, dateOfBirth.toString());
            updatePatient.setString(4, phoneNumber);
            updatePatient.setString(5, insurance);
            updatePatient.setString(6, ramq);
            updatePatient.executeUpdate();
            return id;
        }

        insertPatient.setString(1, firstName);
        insertPatient.setString(2, lastName);
        insertPatient.setString(3, dateOfBirth.toString());
        insertPatient.setString(4, phoneNumber);
        insertPatient.setString(5, ramq);
        insertPatient.setString(6, insurance);
        insertPatient.executeUpdate();
        try (ResultSet keys = insertPatient.getGeneratedKeys()) {
            keys.next();
            int newId = keys.getInt(1);
            patientCache.put(ramq, newId);
            return newId;
        }
    }

    private Integer cachedOrFoundPatientId(String ramq) throws SQLException {
        Integer cached = patientCache.get(ramq);
        if (cached != null) {
            return cached;
        }
        findPatientByRamq.setString(1, ramq);
        try (ResultSet rs = findPatientByRamq.executeQuery()) {
            if (rs.next()) {
                int id = rs.getInt(1);
                patientCache.put(ramq, id);
                return id;
            }
        }
        return null;
    }

    public List<PatientRow> findAllPatients() throws SQLException {
        List<PatientRow> rows = new ArrayList<>();
        try (ResultSet rs = selectAllPatients.executeQuery()) {
            while (rs.next()) {
                rows.add(new PatientRow(
                        rs.getInt("id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("date_of_birth"),
                        rs.getString("phone_number"),
                        rs.getString("ramq"),
                        rs.getString("insurance")));
            }
        }
        return rows;
    }

    // referral

    // patientId comes from saveOrUpdatePatient(...)
    public int addReferral(int patientId, String referringPhysician, String referralType,
                           String requestedSpecialty, String requestedProfessional,
                           String clinicalReason, String priority, String status) throws SQLException {
        insertReferral.setInt(1, patientId);
        insertReferral.setString(2, referringPhysician);
        insertReferral.setString(3, referralType);
        insertReferral.setString(4, requestedSpecialty);
        insertReferral.setString(5, requestedProfessional);
        insertReferral.setString(6, clinicalReason);
        insertReferral.setString(7, priority);
        insertReferral.setString(8, status);
        insertReferral.executeUpdate();
        try (ResultSet keys = insertReferral.getGeneratedKeys()) {
            keys.next();
            return keys.getInt(1);
        }
    }

    public boolean updateReferralStatus(int referralId, String status) throws SQLException {
        updateReferralStatus.setString(1, status);
        updateReferralStatus.setInt(2, referralId);
        return updateReferralStatus.executeUpdate() > 0;
    }

    // appointment

    // referralId may be null when the service does not require a referral.
    // status is the AppointmentStatus name (e.g. appointment.getStatus().name()).
    public int addAppointment(int professionalId, int patientId, int clinicId, int serviceId,
                              Integer referralId, LocalDate date, LocalTime start, LocalTime end,
                              String status) throws SQLException {
        insertAppointment.setInt(1, professionalId);
        insertAppointment.setInt(2, patientId);
        insertAppointment.setInt(3, clinicId);
        insertAppointment.setInt(4, serviceId);
        if (referralId == null) {
            insertAppointment.setNull(5, Types.INTEGER);
        } else {
            insertAppointment.setInt(5, referralId);
        }
        insertAppointment.setString(6, date.toString());
        insertAppointment.setString(7, start.toString());
        insertAppointment.setString(8, end.toString());
        insertAppointment.setString(9, status);
        insertAppointment.executeUpdate();
        try (ResultSet keys = insertAppointment.getGeneratedKeys()) {
            keys.next();
            return keys.getInt(1);
        }
    }

    // used by confirmAppointment() / cancelAppointment()
    public boolean updateAppointmentStatus(int professionalId, LocalDate date,
                                           LocalTime start, String status) throws SQLException {
        updateAppointmentStatus.setString(1, status);
        updateAppointmentStatus.setInt(2, professionalId);
        updateAppointmentStatus.setString(3, date.toString());
        updateAppointmentStatus.setString(4, start.toString());
        return updateAppointmentStatus.executeUpdate() > 0;
    }

    public boolean removeAppointment(int professionalId, LocalDate date,
                                     LocalTime start) throws SQLException {
        deleteAppointment.setInt(1, professionalId);
        deleteAppointment.setString(2, date.toString());
        deleteAppointment.setString(3, start.toString());
        return deleteAppointment.executeUpdate() > 0;
    }

    public List<AppointmentRow> findAllAppointments() throws SQLException {
        List<AppointmentRow> rows = new ArrayList<>();
        try (ResultSet rs = selectAllAppointments.executeQuery()) {
            while (rs.next()) {
                int referral = rs.getInt("referral_id");
                Integer referralId = rs.wasNull() ? null : referral;
                rows.add(new AppointmentRow(
                        rs.getInt("id"),
                        rs.getInt("professional_id"),
                        rs.getInt("patient_id"),
                        rs.getInt("clinic_id"),
                        rs.getInt("service_id"),
                        referralId,
                        rs.getString("date"),
                        rs.getString("start_time"),
                        rs.getString("end_time"),
                        rs.getString("status")));
            }
        }
        return rows;
    }

// cleanup

    private void closeStatementsQuietly() {
        for (PreparedStatement ps : statements) {
            try {
                ps.close();
            } catch (SQLException ignored) {
                // nothing useful to do while cleaning up
            }
        }
    }

    // forced by the AutoCloseable interface to clear everything
    @Override
    public void close() throws SQLException {
        closeStatementsQuietly();
        conn.close();
    }
}