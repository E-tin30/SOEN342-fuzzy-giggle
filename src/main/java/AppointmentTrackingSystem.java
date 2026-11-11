import database.DatabaseGateway;
import domain.Availability;
import services.AvailibilityService;
import services.CSVService;
import utils.ConsolePrinter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;


//main class of the application
public class AppointmentTrackingSystem {
    public static void main(String[] args) {

        try {
            Files.createDirectories(Path.of("data"));

            try (DatabaseGateway db = new DatabaseGateway("jdbc:sqlite:data/app.db")) {
                //clear db on start
                db.clearAll();

                //services
                CSVService csvService = new CSVService(db);
                AvailibilityService availabilityService = new AvailibilityService(db);

                Scanner scanner = new Scanner(System.in);

                ConsolePrinter.greeting();

                //Main loop - loops through main menu until the user chooses the exit option.
                while (true) {
                    int menuSelection = 0;
                    do {
                        ConsolePrinter.printMainMenu();
                        try {
                            menuSelection = Integer.parseInt(scanner.nextLine().trim());
                        } catch (Exception e) {
                            ConsolePrinter.error("Invalid selection: must be a number.");
                        }
                    } while (menuSelection == 0);

                    switch (menuSelection) { //searchForPatient()
                        case 1 -> {

                        }
                        case 2 -> { //Book appointment

                        }
                        case 3 -> { //Search for availability
                            //first import availabilities from db:
                            try {
                                List<Availability> availabilities = availabilityService.getAllAvailibilities();
                            } catch (SQLException e) {
                                ConsolePrinter.error("Error while trying to get the availabilities.");
                                continue;
                            }

                            //TODO: Do something and filer the list from above
                        }
                        case 4 -> { //make availability for professional

                        }
                        case 5 -> {
                            //import from csv
                            System.out.print("Insert the path to the CSV file from which you want to add availabilities: ");
                            Path file = Path.of(scanner.nextLine().trim());
                            try {
                                csvService.importAvailabilitiesFromCSV(file);
                            } catch (Exception e) {
                                ConsolePrinter.error("Unable to add availabilities.");
                            }
                        }
                        case 6 -> {
                            //export availabilities to CSV
                            System.out.print("Path of the CSV file to create: ");
                            Path file = Path.of(scanner.nextLine().trim());
                            try {
                                csvService.exportAvailabilitiesToCSV(file);
                            } catch (Exception e) {
                                ConsolePrinter.error("Unable to export availabilities to CSV.");
                            }
                        }
                        case 7 -> { //exit
                            scanner.close();
                            ConsolePrinter.goodbye();
                            System.exit(0);
                            return;
                        }
                        default -> ConsolePrinter.error("Invalid menu selection: not a valid number.");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Could not start: " + e.getMessage());
            System.exit(1);
        }
    }
}
