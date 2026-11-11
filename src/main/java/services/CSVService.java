package services;

import database.AvailabilityRow;
import database.DatabaseGateway;
import lombok.RequiredArgsConstructor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

//note: the following character needs to be handled if using Excel as import or export according to sources "\uFEFF"

@RequiredArgsConstructor
public class CSVService {
    private static final String HEADER = "first_name,last_name,title,specialization,date,start,end";
    private static final int COLUMNS = 7;

    private final DatabaseGateway db;

    public void importAvailabilitiesFromCSV(Path file) throws IOException, SQLException {
        int added = 0;
        int skipped = 0;

        db.begin();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                //check for header row exists in file
                if (lineNo == 1) {
                    if (line.toLowerCase().startsWith("first_name")) {
                        continue;
                    }
                }
                if (line.isBlank()) {
                    continue;
                }

                List<String> c = parseLine(line);
                if (c.size() != COLUMNS) {
                    System.out.println("Line " + lineNo + ": expected " + COLUMNS + " columns, skipped.");
                    skipped++;
                    continue;
                }

                try {
                    String first_name = c.get(0).trim();
                    String last_name = c.get(1).trim();
                    String title = c.get(2).trim();
                    String specialization = c.get(3).trim();
                    LocalDate date = LocalDate.parse(c.get(4).trim());
                    LocalTime start = LocalTime.parse(c.get(5).trim());
                    LocalTime end = LocalTime.parse(c.get(6).trim());

                    if (first_name.isEmpty() || last_name.isEmpty()) {
                        System.out.println("Line " + lineNo + ": missing name, skipped.");
                        skipped++;
                        continue;
                    }
                    if (!end.isAfter(start)) {
                        System.out.println("Line " + lineNo + ": end is not after start, skipped.");
                        skipped++;
                        continue;
                    }

                    int professionalId = db.findOrCreateProfessional(first_name, last_name, title, specialization);
                    if (db.addAvailability(professionalId, date, start, end)) {
                        added++;
                    } else {
                        System.out.println("Line " + lineNo + ": duplicate availability, skipped.");
                        skipped++;
                    }
                } catch (DateTimeParseException e) {
                    System.out.println("Line " + lineNo + ": invalid date/time, skipped.");
                    skipped++;
                }
            }
            db.commit();
        } catch (Exception e) {
            db.rollback();
            throw e;
        }
        System.out.println("Import completed: added " + added + " availabilities and skipped " + skipped + " lines from CSV.");
    }

    public void exportAvailabilitiesToCSV(Path file) throws IOException, SQLException {
        System.out.println("Exporting availabilities to CSV...");

        List<AvailabilityRow> rows = db.findAllAvailabilities();

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write(HEADER);
            writer.newLine();
            for (AvailabilityRow row : rows) {
                writer.write(String.join(",",
                        row.first_name(),
                        row.last_name(),
                        row.title(),
                        row.specialization(),
                        row.date(),
                        row.start(),
                        row.end()));
                writer.newLine();
            }
        }

        System.out.println("Finished exporting availabilities to CSV. You can see them at path: " + file.toAbsolutePath());
    }

    // helper method to parse a line (assumes that each row has the fields delimited by commas
    private static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        fields.add(current.toString());
        return fields;
    }

}
