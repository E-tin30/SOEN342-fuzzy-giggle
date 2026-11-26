package services;

import database.DatabaseGateway;
import database.ProfessionalRow;
import domain.Professional;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class ProfessionalService {
    private final DatabaseGateway db;

    // returns false if this professional already exists
    public boolean registerProfessional(String first, String last, String title, String specialization,
                                        List<String> serviceNames) throws SQLException {
        if (first == null || last == null || title == null || specialization == null
                || first.isBlank() || last.isBlank() || title.isBlank() || specialization.isBlank()) {
            throw new IllegalArgumentException("All fields are required.");
        }
        db.begin();
        try {
            int id = db.addProfessional(first, last, title, specialization);
            if (id < 0) {
                db.rollback();
                return false;
            }
            for (String name : serviceNames) {
                db.linkProfessionalToService(id, name);
            }
            db.commit();
            return true;
        } catch (Exception e) {
            db.rollback();
            throw e;
        }
    }

    public List<Professional> getAllProfessionals() throws SQLException {
        List<Professional> professionals = new ArrayList<>();
        for (ProfessionalRow r : db.findAllProfessionals()) {
            professionals.add(new Professional(r.id(), r.firstName(), r.lastName(), r.title(), r.specialization()));
        }
        return professionals;
    }
}