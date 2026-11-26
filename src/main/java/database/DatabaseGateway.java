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

    private final Connection conn;
    private final PreparedStatement findProfessional;
    private final PreparedStatement insertProfessional;
    private final PreparedStatement insertAvailability;
    private final PreparedStatement removeAvailability;
    private final PreparedStatement selectAllAvailabilities;
    private final PreparedStatement insertPatient;
    private final PreparedStatement selectAllPatients;
    private final PreparedStatement selectPatientCount;
    private final PreparedStatement selectProfessionalCount;
    private final PreparedStatement selectAllProfessionals;
    private final PreparedStatement insertService;
    private final PreparedStatement selectAllServices;
    private final PreparedStatement selectServiceCount;
    private final PreparedStatement linkProfessionalToService;
    private final PreparedStatement linkProfessionalToSpecialization;
    private final PreparedStatement selectServicesOfProfessional;
    private final PreparedStatement insertResource;
    private final PreparedStatement selectAllResources;
    private final PreparedStatement selectResourceCount;
    private final PreparedStatement linkServiceToResource;
    private final PreparedStatement selectResourcesOfService;
    private final PreparedStatement insertWaitlistEntry;
    private final PreparedStatement selectActiveWaitlist;
    private final PreparedStatement updateWaitlistPriority;
    private final PreparedStatement updateWaitlistStatus;

    private final Map<List<String>, Integer> professionalCache = new HashMap<>();

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
                        CREATE TABLE IF NOT EXISTS patient (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            first_name TEXT NOT NULL,
                            last_name TEXT NOT NULL,
                            date_of_birth TEXT NOT NULL,
                            phone_number TEXT NOT NULL,
                            ramq TEXT NOT NULL UNIQUE,
                            insurance TEXT NOT NULL
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS service (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL UNIQUE,
                            specialization TEXT NOT NULL,
                            requires_referral INTEGER NOT NULL DEFAULT 0,
                            duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0)
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS professional_service (
                            professional_id INTEGER NOT NULL REFERENCES professional(id),
                            service_id INTEGER NOT NULL REFERENCES service(id),
                            PRIMARY KEY (professional_id, service_id)
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS resource (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL UNIQUE,
                            type TEXT NOT NULL CHECK (type IN ('ROOM', 'EQUIPMENT', 'ROLE'))
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS service_resource (
                            service_id INTEGER NOT NULL REFERENCES service(id),
                            resource_id INTEGER NOT NULL REFERENCES resource(id),
                            PRIMARY KEY (service_id, resource_id)
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS waitlist_entry (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            patient_id INTEGER NOT NULL REFERENCES patient(id),
                            professional_id INTEGER REFERENCES professional(id),
                            specialization TEXT,
                            date_from TEXT,
                            date_to TEXT,
                            priority INTEGER NOT NULL DEFAULT 3 CHECK (priority BETWEEN 1 AND 5),
                            status TEXT NOT NULL DEFAULT 'WAITING'
                                CHECK (status IN ('WAITING', 'OFFERED', 'REMOVED', 'BOOKED')),
                            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            CHECK (professional_id IS NOT NULL OR specialization IS NOT NULL)
                        )""");
            }

            findProfessional = conn.prepareStatement(
                    "SELECT id FROM professional "
                            + "WHERE first_name=? AND last_name=? AND title=? AND specialization=?");
            insertProfessional = conn.prepareStatement(
                    "INSERT INTO professional(first_name, last_name, title, specialization) VALUES(?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            insertAvailability = conn.prepareStatement(
                    "INSERT OR IGNORE INTO availability(date, start_time, end_time, professional_id, is_booked) "
                            + "VALUES(?,?,?,?,?)");
            removeAvailability = conn.prepareStatement(
                    "DELETE FROM availability WHERE professional_id = ? AND date = ? AND start_time = ?");
            selectAllAvailabilities = conn.prepareStatement("""
                    SELECT p.id, p.first_name, p.last_name, p.title, p.specialization,
                           a.date, a.start_time, a.end_time, a.is_booked
                    FROM availability a
                    JOIN professional p ON p.id = a.professional_id
                    ORDER BY p.last_name, p.first_name, a.date, a.start_time
                    """);
            insertPatient = conn.prepareStatement("INSERT OR IGNORE INTO patient(first_name, last_name, date_of_birth, phone_number, ramq, insurance) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            selectAllPatients = conn.prepareStatement(
                    "SELECT id, first_name, last_name, date_of_birth, phone_number, ramq, insurance "+ "FROM patient ORDER BY last_name, first_name");
            selectPatientCount = conn.prepareStatement("SELECT COUNT(*) FROM patient");
            selectProfessionalCount = conn.prepareStatement("SELECT COUNT(*) FROM professional");
            selectAllProfessionals = conn.prepareStatement(
                    "SELECT id, first_name, last_name, title, specialization FROM professional ORDER BY last_name, first_name");
            insertService = conn.prepareStatement(
                    "INSERT OR IGNORE INTO service(name, specialization, requires_referral, duration_minutes) VALUES(?,?,?,?)");
            selectAllServices = conn.prepareStatement(
                    "SELECT name, specialization, requires_referral, duration_minutes FROM service ORDER BY id");
            selectServiceCount = conn.prepareStatement("SELECT COUNT(*) FROM service");
            linkProfessionalToService = conn.prepareStatement(
                    "INSERT OR IGNORE INTO professional_service(professional_id, service_id) "
                            + "SELECT ?, id FROM service WHERE name = ?");
            linkProfessionalToSpecialization = conn.prepareStatement(
                    "INSERT OR IGNORE INTO professional_service(professional_id, service_id) "
                            + "SELECT ?, id FROM service WHERE specialization = ?");
            selectServicesOfProfessional = conn.prepareStatement("""
                    SELECT s.name, s.specialization, s.requires_referral, s.duration_minutes
                    FROM professional_service ps
                    JOIN service s ON s.id = ps.service_id
                    WHERE ps.professional_id = ?
                    ORDER BY s.id
                    """);
            insertResource = conn.prepareStatement("INSERT OR IGNORE INTO resource(name, type) VALUES(?,?)");
            selectAllResources = conn.prepareStatement("SELECT id, name, type FROM resource ORDER BY id");
            selectResourceCount = conn.prepareStatement("SELECT COUNT(*) FROM resource");
            linkServiceToResource = conn.prepareStatement(
                    "INSERT OR IGNORE INTO service_resource(service_id, resource_id) "
                            + "SELECT s.id, r.id FROM service s, resource r WHERE s.name = ? AND r.name = ?");
            selectResourcesOfService = conn.prepareStatement("""
                    SELECT r.id, r.name, r.type
                    FROM service_resource sr
                    JOIN service s ON s.id = sr.service_id
                    JOIN resource r ON r.id = sr.resource_id
                    WHERE s.name = ?
                    ORDER BY r.id
                    """);
            insertWaitlistEntry = conn.prepareStatement(
                    "INSERT INTO waitlist_entry(patient_id, professional_id, specialization, date_from, date_to, priority) "
                            + "VALUES(?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            selectActiveWaitlist = conn.prepareStatement("""
                    SELECT id, patient_id, professional_id, specialization, date_from, date_to, priority, status
                    FROM waitlist_entry
                    WHERE status IN ('WAITING', 'OFFERED')
                    ORDER BY priority, created_at, id
                    """);
            updateWaitlistPriority = conn.prepareStatement("UPDATE waitlist_entry SET priority = ? WHERE id = ?");
            updateWaitlistStatus = conn.prepareStatement("UPDATE waitlist_entry SET status = ? WHERE id = ?");
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
    }

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
        professionalCache.clear();
    }

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

    // adds an availability into the database
    public boolean addAvailability(int professional_id, LocalDate date,
                                   LocalTime start, LocalTime end, boolean isBooked) throws SQLException {
        insertAvailability.setString(1, date.toString());   // yyyy-MM-dd
        insertAvailability.setString(2, start.toString());  // HH:mm
        insertAvailability.setString(3, end.toString());
        insertAvailability.setInt(4, professional_id);
        insertAvailability.setBoolean(5, isBooked);
        return insertAvailability.executeUpdate() > 0;
    }

    //removes availability from database
    public boolean removeAvailability(Availability availability) throws SQLException {
        removeAvailability.setInt(1, availability.getProfessional().getId());
        removeAvailability.setString(2, availability.getDate().toString());
        removeAvailability.setString(3, availability.getStart().toString());
        return removeAvailability.executeUpdate() > 0;
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

    // adds a patient, returns false if the RAMQ number already exists
    public int addPatient(String first, String last, LocalDate dob, String phone, String ramq, String insurance) throws SQLException {
        insertPatient.setString(1, first);
        insertPatient.setString(2, last);
        insertPatient.setString(3, dob.toString());
        insertPatient.setString(4, phone);
        insertPatient.setString(5, ramq);
        insertPatient.setString(6, insurance);
        if (insertPatient.executeUpdate() == 0) return -1;
        try (ResultSet keys = insertPatient.getGeneratedKeys()) {
            keys.next();
            return keys.getInt(1);
        }
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

    public int countPatients() throws SQLException {
        try (ResultSet rs = selectPatientCount.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public int countProfessionals() throws SQLException {
        try (ResultSet rs = selectProfessionalCount.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // creates a professional, returns the new id or -1 if it already exists
    public int addProfessional(String firstName, String lastName, String title, String specialization) throws SQLException {
        findProfessional.setString(1, firstName);
        findProfessional.setString(2, lastName);
        findProfessional.setString(3, title);
        findProfessional.setString(4, specialization);
        try (ResultSet rs = findProfessional.executeQuery()) {
            if (rs.next()) return -1;
        }
        return findOrCreateProfessional(firstName, lastName, title, specialization);
    }

    public List<ProfessionalRow> findAllProfessionals() throws SQLException {
        List<ProfessionalRow> rows = new ArrayList<>();
        try (ResultSet rs = selectAllProfessionals.executeQuery()) {
            while (rs.next()) {
                rows.add(new ProfessionalRow(rs.getInt("id"), rs.getString("first_name"),
                        rs.getString("last_name"), rs.getString("title"), rs.getString("specialization")));
            }
        }
        return rows;
    }

    public boolean addService(String name, String specialization, boolean requiresReferral, int minutes) throws SQLException {
        insertService.setString(1, name);
        insertService.setString(2, specialization);
        insertService.setBoolean(3, requiresReferral);
        insertService.setInt(4, minutes);
        return insertService.executeUpdate() > 0;
    }

    public List<ServiceRow> findAllServices() throws SQLException {
        List<ServiceRow> rows = new ArrayList<>();
        try (ResultSet rs = selectAllServices.executeQuery()) {
            while (rs.next()) {
                rows.add(new ServiceRow(rs.getString("name"), rs.getString("specialization"),
                        rs.getBoolean("requires_referral"), rs.getInt("duration_minutes")));
            }
        }
        return rows;
    }

    public int countServices() throws SQLException {
        try (ResultSet rs = selectServiceCount.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public boolean linkProfessionalToService(int professionalId, String serviceName) throws SQLException {
        linkProfessionalToService.setInt(1, professionalId);
        linkProfessionalToService.setString(2, serviceName);
        return linkProfessionalToService.executeUpdate() > 0;
    }

    // links the professional to every service tagged with that specialization
    public boolean linkProfessionalToSpecialization(int professionalId, String specialization) throws SQLException {
        linkProfessionalToSpecialization.setInt(1, professionalId);
        linkProfessionalToSpecialization.setString(2, specialization);
        return linkProfessionalToSpecialization.executeUpdate() > 0;
    }

    public List<ServiceRow> findServicesOfProfessional(int professionalId) throws SQLException {
        List<ServiceRow> rows = new ArrayList<>();
        selectServicesOfProfessional.setInt(1, professionalId);
        try (ResultSet rs = selectServicesOfProfessional.executeQuery()) {
            while (rs.next()) {
                rows.add(new ServiceRow(rs.getString("name"), rs.getString("specialization"),
                        rs.getBoolean("requires_referral"), rs.getInt("duration_minutes")));
            }
        }
        return rows;
    }

    // returns false if a resource with this name already exists
    public boolean addResource(String name, String type) throws SQLException {
        insertResource.setString(1, name);
        insertResource.setString(2, type);
        return insertResource.executeUpdate() > 0;
    }

    public List<ResourceRow> findAllResources() throws SQLException {
        List<ResourceRow> rows = new ArrayList<>();
        try (ResultSet rs = selectAllResources.executeQuery()) {
            while (rs.next()) {
                rows.add(new ResourceRow(rs.getInt("id"), rs.getString("name"), rs.getString("type")));
            }
        }
        return rows;
    }

    public int countResources() throws SQLException {
        try (ResultSet rs = selectResourceCount.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public boolean linkServiceToResource(String serviceName, String resourceName) throws SQLException {
        linkServiceToResource.setString(1, serviceName);
        linkServiceToResource.setString(2, resourceName);
        return linkServiceToResource.executeUpdate() > 0;
    }

    public List<ResourceRow> findResourcesOfService(String serviceName) throws SQLException {
        List<ResourceRow> rows = new ArrayList<>();
        selectResourcesOfService.setString(1, serviceName);
        try (ResultSet rs = selectResourcesOfService.executeQuery()) {
            while (rs.next()) {
                rows.add(new ResourceRow(rs.getInt("id"), rs.getString("name"), rs.getString("type")));
            }
        }
        return rows;
    }

    // returns the new entry id
    public int addWaitlistEntry(int patientId, Integer professionalId, String specialization,
                                LocalDate from, LocalDate to, int priority) throws SQLException {
        insertWaitlistEntry.setInt(1, patientId);
        if (professionalId == null) {
            insertWaitlistEntry.setNull(2, Types.INTEGER);
        } else {
            insertWaitlistEntry.setInt(2, professionalId);
        }
        insertWaitlistEntry.setString(3, specialization);
        insertWaitlistEntry.setString(4, from == null ? null : from.toString());
        insertWaitlistEntry.setString(5, to == null ? null : to.toString());
        insertWaitlistEntry.setInt(6, priority);
        insertWaitlistEntry.executeUpdate();
        try (ResultSet keys = insertWaitlistEntry.getGeneratedKeys()) {
            keys.next();
            return keys.getInt(1);
        }
    }

    // entries still waiting or offered, highest priority first
    public List<WaitlistRow> findActiveWaitlist() throws SQLException {
        List<WaitlistRow> rows = new ArrayList<>();
        try (ResultSet rs = selectActiveWaitlist.executeQuery()) {
            while (rs.next()) {
                int professionalId = rs.getInt("professional_id");
                Integer professional = rs.wasNull() ? null : professionalId;
                rows.add(new WaitlistRow(rs.getInt("id"), rs.getInt("patient_id"), professional,
                        rs.getString("specialization"), rs.getString("date_from"), rs.getString("date_to"),
                        rs.getInt("priority"), rs.getString("status")));
            }
        }
        return rows;
    }

    public boolean setWaitlistPriority(int id, int priority) throws SQLException {
        updateWaitlistPriority.setInt(1, priority);
        updateWaitlistPriority.setInt(2, id);
        return updateWaitlistPriority.executeUpdate() > 0;
    }

    public boolean setWaitlistStatus(int id, String status) throws SQLException {
        updateWaitlistStatus.setString(1, status);
        updateWaitlistStatus.setInt(2, id);
        return updateWaitlistStatus.executeUpdate() > 0;
    }

    //added here temporarily because the system must start from fresh at the beginning of every run (I think it will be removed in iteration 3)
//    public void clearAll() throws SQLException {
//        try (Statement st = conn.createStatement()) {
//            st.executeUpdate("DELETE FROM availability");
//            st.executeUpdate("DELETE FROM professional");
//            // resets ids at 1
//            st.executeUpdate("DELETE FROM sqlite_sequence WHERE name IN ('availability', 'professional')");
//        }
//        professionalCache.clear();
//    }

    // forced by the AutoCloseable interface to clear everything
    @Override
    public void close() throws SQLException {
        findProfessional.close();
        insertProfessional.close();
        insertAvailability.close();
        selectAllAvailabilities.close();
        removeAvailability.close();
        insertPatient.close();
        selectAllPatients.close();
        selectPatientCount.close();
        selectProfessionalCount.close();
        selectAllProfessionals.close();
        insertService.close();
        selectAllServices.close();
        selectServiceCount.close();
        linkProfessionalToService.close();
        linkProfessionalToSpecialization.close();
        selectServicesOfProfessional.close();
        insertResource.close();
        selectAllResources.close();
        selectResourceCount.close();
        linkServiceToResource.close();
        selectResourcesOfService.close();
        insertWaitlistEntry.close();
        selectActiveWaitlist.close();
        updateWaitlistPriority.close();
        updateWaitlistStatus.close();
        conn.close();
    }
}