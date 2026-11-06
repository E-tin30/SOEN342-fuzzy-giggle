package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    private final PreparedStatement selectAllAvailabilities;

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
                            professional_id INTEGER NOT NULL REFERENCES professional(id),
                            UNIQUE(professional_id, date, start_time)
                        )""");
            }

            findProfessional = conn.prepareStatement(
                    "SELECT id FROM professional "
                            + "WHERE first_name=? AND last_name=? AND title=? AND specialization=?");
            insertProfessional = conn.prepareStatement(
                    "INSERT INTO professional(first_name, last_name, title, specialization) VALUES(?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            insertAvailability = conn.prepareStatement(
                    "INSERT OR IGNORE INTO availability(date, start_time, end_time, professional_id) "
                            + "VALUES(?,?,?,?)");
            selectAllAvailabilities = conn.prepareStatement("""
                    SELECT p.id, p.first_name, p.last_name, p.title, p.specialization,
                           a.date, a.start_time, a.end_time
                    FROM availability a
                    JOIN professional p ON p.id = a.professional_id
                    ORDER BY p.last_name, p.first_name, a.date, a.start_time
                    """);
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
                                   LocalTime start, LocalTime end) throws SQLException {
        insertAvailability.setString(1, date.toString());   // yyyy-MM-dd
        insertAvailability.setString(2, start.toString());  // HH:mm
        insertAvailability.setString(3, end.toString());
        insertAvailability.setInt(4, professional_id);
        return insertAvailability.executeUpdate() > 0;
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
                        rs.getString("end_time")));
            }
        }
        return rows;
    }

    // forced by the AutoCloseable interface to clear everything
    @Override
    public void close() throws SQLException {
        findProfessional.close();
        insertProfessional.close();
        insertAvailability.close();
        selectAllAvailabilities.close();
        conn.close();
    }
}