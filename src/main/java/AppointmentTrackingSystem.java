import controllers.AppointmentController;
import controllers.AvailabilityController;
import controllers.ReferralRequiredException;
import controllers.ServiceNotOfferedException;
import database.DatabaseGateway;
import domain.ActivityLog;
import domain.Appointment;
import domain.Availability;
import domain.Clinic;
import domain.DateRange;
import domain.Patient;
import domain.PatientSearchSpecification;
import domain.Professional;
import domain.Referral;
import domain.RepetitionFrequency;
import domain.RepetitionPattern;
import domain.Service;
import domain.MonthlyPattern;
import domain.WeeklyPattern;
import domain.DailyPattern;
import services.AvailabilityService;
import services.CSVService;
import services.ClinicLoader;
import utils.ConsolePrinter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

//main class of the application
public class AppointmentTrackingSystem {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {

        try {
            Files.createDirectories(Path.of("data"));

            try (DatabaseGateway db = new DatabaseGateway("jdbc:sqlite:data/app.db")) {
                //clear db on start
                db.clearAll();

                //domain
                Clinic clinic = new Clinic("Clinic", "Montreal");
                ActivityLog activityLog = new ActivityLog();
                AppointmentController appointmentController = new AppointmentController(clinic, activityLog);
                AvailabilityController availabilityController = new AvailabilityController(clinic);

                clinic.addPatient(new Patient("Émilie", "Lavoie", Date.valueOf("1990-04-12"), "514-555-0101", "LAVE90041201", "RAMQ"));
                clinic.addPatient(new Patient("Olivier", "Côté", Date.valueOf("1985-09-30"), "514-555-0102", "COTO85093001", "RAMQ"));
                clinic.addPatient(new Patient("Fatima", "Benali", Date.valueOf("1978-01-22"), "438-555-0103", "BENF78012201", "RAMQ"));
                clinic.addPatient(new Patient("Thomas", "Girard", Date.valueOf("2001-06-05"), "514-555-0104", "GIRT01060501", "RAMQ"));
                clinic.addPatient(new Patient("Juliette", "Morin", Date.valueOf("1995-11-18"), "438-555-0105", "MORJ95111801", "RAMQ"));
                clinic.addPatient(new Patient("Alexandre", "Dubois", Date.valueOf("1969-03-09"), "514-555-0106", "DUBA69030901", "Manulife"));
                clinic.addPatient(new Patient("Maya", "Nguyen", Date.valueOf("1999-07-27"), "438-555-0107", "NGUM99072701", "Blue Cross"));
                clinic.addPatient(new Patient("Gabriel", "Fortin", Date.valueOf("1982-12-14"), "514-555-0108", "FORG82121401", "RAMQ"));

                //services
                CSVService csvService = new CSVService(db);
                AvailabilityService availabilityService = new AvailabilityService(db);
                ClinicLoader clinicLoader = new ClinicLoader(clinic, availabilityService);

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

                    switch (menuSelection) {
                        case 1 -> searchPatientFlow(appointmentController);
                        case 2 -> { //Book appointment
                            try {
                                clinicLoader.sync();
                                bookAppointmentFlow(clinic, appointmentController);
                            } catch (Exception e) {
                                ConsolePrinter.error("Error while trying to book: " + e.getMessage());
                            }
                        }
                        case 3 -> { //Search for availability
                            try {
                                clinicLoader.sync();
                                searchAvailabilityFlow(clinic, availabilityController);
                            } catch (Exception e) {
                                ConsolePrinter.error("Error while trying to get the availabilities: " + e.getMessage());
                            }
                        }
                        case 4 -> { //make availability for professional
                            clinicLoader.sync();
                            String enteredProfessionalName = ask("Enter professional name: ");

                            makeAvailabilityFlow();

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

    // helpers

    private static String ask(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    // returns the chosen index (0-based), or -1 if the input is blank / invalid
    private static int pick(String prompt, int size) {
        String in = ask(prompt);
        try {
            int n = Integer.parseInt(in);
            if (n >= 1 && n <= size) return n - 1;
        } catch (NumberFormatException ignored) { }
        return -1;
    }

    private static Date askDate(String prompt) {
        String in = ask(prompt);
        if (in.isEmpty()) return null;
        try {
            return Date.valueOf(in);
        } catch (IllegalArgumentException e) {
            ConsolePrinter.error("Invalid date, expected yyyy-mm-dd.");
            return null;
        }
    }

    // case 1

    private static List<Patient> searchPatientFlow(AppointmentController controller) {
        ConsolePrinter.printSearchCriterion();
        int criterion;
        try {
            criterion = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            ConsolePrinter.error("Invalid selection: must be a number.");
            return List.of();
        }
        if (criterion == 6) return List.of();

        String ramq = null, phone = null, first = null, last = null;
        Date dob = null;

        switch (criterion) {
            case 1 -> first = ask("First name: ");
            case 2 -> last = ask("Last name: ");
            case 3 -> ramq = ask("Insurance (RAMQ) number: ");
            case 4 -> phone = ask("Phone number: ");
            case 5 -> {
                dob = askDate("Date of birth (yyyy-mm-dd): ");
                if (dob == null) return List.of();
            }
            default -> {
                ConsolePrinter.error("Invalid selection.");
                return List.of();
            }
        }

        try {
            List<Patient> results = controller.findPatient(
                    new PatientSearchSpecification(ramq, dob, phone, first, last));
            ConsolePrinter.printPatients(results);
            return results;
        } catch (IllegalArgumentException e) {
            ConsolePrinter.error(e.getMessage());
            return List.of();
        }
    }

    // case 2

    private static void bookAppointmentFlow(Clinic clinic, AppointmentController controller) {
        // 1. find and select the patient
        List<Patient> patients = searchPatientFlow(controller);
        if (patients.isEmpty()) return;
        int p = pick("Pick the patient (number, blank to cancel): ", patients.size());
        if (p < 0) return;
        controller.selectPatient(patients.get(p));

        // 2. choose the service
        List<Service> services = clinic.getOfferedServices();
        ConsolePrinter.printServices(services);
        int s = pick("Pick the service (number, blank to cancel): ", services.size());
        if (s < 0) return;
        Service service = services.get(s);

        int minutes = service.getDurationMinutes();

        // 3. request the appointment (may need a referral first)
        List<Availability> options;
        try {
            options = controller.requestNewAppointment(service);
        } catch (ServiceNotOfferedException e) {
            ConsolePrinter.error("This clinic does not offer that service.");
            return;
        } catch (ReferralRequiredException e) {
            System.out.println("\nThis service requires a referral.");
            Referral referral = askReferral(service);
            if (referral == null) return;
            options = controller.provideReferral(referral);
        }

        if (options.isEmpty()) {
            ConsolePrinter.error("No availability for that service.");
            return;
        }

        // 4. choose a slot
        ConsolePrinter.printAvailabilities(options, minutes);
        int a = pick("Pick an availability (number, blank to cancel): ", options.size());
        if (a < 0) return;
        Availability chosen = options.get(a);

        Time start = chosen.firstFit(minutes);
        String in = ask("Start time (hh:mm, blank = " + start.toString().substring(0, 5) + "): ");
        if (!in.isEmpty()) {
            try {
                start = Time.valueOf(in + ":00");
            } catch (IllegalArgumentException e) {
                ConsolePrinter.error("Invalid time, expected hh:mm.");
                return;
            }
        }

        Appointment appointment = controller.select(chosen, start);
        if (appointment == null) {
            ConsolePrinter.error("That time doesn't work: a " + minutes + "-minute appointment must start and end inside the availability and not overlap an appointment that is already booked.");
            return;
        }
        ConsolePrinter.printAppointment(appointment);
    }

    private static Referral askReferral(Service service) {
        String physician = ask("Referring physician (blank to cancel): ");
        if (physician.isEmpty()) return null;
        String type = ask("Referral type: ");
        String reason = ask("Clinical reason: ");
        String priority = ask("Priority: ");
        return new Referral(physician, type, service.getName(), "", reason, priority, "VALID");
    }

    // case 3

    private static void searchAvailabilityFlow(Clinic clinic, AvailabilityController controller) {
        // optional service filter
        Service service = null;
        List<Service> services = clinic.getOfferedServices();
        ConsolePrinter.printServices(services);
        int s = pick("Filter by service (number, blank for any): ", services.size());
        if (s >= 0) service = services.get(s);

        // optional professional filter
        domain.Professional professional = null;
        List<domain.Professional> professionals = clinic.getProfessionals();
        ConsolePrinter.printProfessionals(professionals);
        int p = pick("Filter by professional (number, blank for any): ", professionals.size());
        if (p >= 0) professional = professionals.get(p);

        // optional date range filter
        DateRange range = null;
        Date from = askDate("From date (yyyy-mm-dd, blank for any): ");
        if (from != null) {
            Date to = askDate("To date (yyyy-mm-dd, blank = same day): ");
            range = new DateRange(from, to == null ? from : to);
        }

        int minutes = (service == null) ? 1 : service.getDurationMinutes();

        List<Availability> results = controller.searchAvailability(service, professional, range);
        if (results.isEmpty()) {
            ConsolePrinter.error("No availability found.");
        } else {
            ConsolePrinter.printAvailabilities(results, minutes);
        }
    }

    //case 4
    private static void makeAvailabilityFlow(Clinic clinic, AvailabilityController controller){

    };


    private static Professional selectProfessional(Clinic clinic) {
        List<Professional> professionals = clinic.getProfessionals();
        if (professionals.isEmpty()) {
            ConsolePrinter.error("No professionals available.");
            return null;
        }
        ConsolePrinter.printProfessionals(professionals);
        int selection = pick("Select a professional (number, blank to cancel): ",professionals.size());

        if (selection < 0) {
            return null;
        }

        return professionals.get(selection);
    }

    private static RepetitionPattern askRepetitionPattern(){
        System.out.println("""        
            Select a repetition pattern:
            1. Daily
            2. Weekly
            3. Monthly
            """);

        int choice = pick("Selection (blank to cancel): ", 3);

        switch (choice) {
            case 0 -> {
                return new DailyPattern();
            }

            case 1 -> {
                String inputDaysOfTheWeek = ask("Days of week (1=Mon, 2=Tue, ..., 7=Sun; comma-separated): ");
                if (inputDaysOfTheWeek.isBlank()) return null;
                Set<DayOfWeek> daysOfTheWeek = EnumSet.noneOf(DayOfWeek.class);
                try {
                    for (String value : inputDaysOfTheWeek.split(",")) {
                        int dayOfTheWeek = Integer.parseInt(value.trim());
                        if (dayOfTheWeek < 1 || dayOfTheWeek > 7) {
                            throw new IllegalArgumentException();
                        }
                        daysOfTheWeek.add(DayOfWeek.of(dayOfTheWeek));
                    }
                } catch (IllegalArgumentException e) {
                    ConsolePrinter.error("Invalid day selection.");
                    return null;
                }
                return new WeeklyPattern(daysOfTheWeek);
            }


            case 2 -> {
                String inputDaysOfTheMonth = ask("Days of month (1-31, comma-separated): ");
                if (inputDaysOfTheMonth.isBlank()) return null;
                Set<Integer> daysOfTheMonth = new HashSet<>();
                try {
                    for (String value : inputDaysOfTheMonth.split(",")) {
                        int dayOfTheMonth = Integer.parseInt(value.trim());
                        if (dayOfTheMonth < 1 || dayOfTheMonth > 31) {
                            throw new IllegalArgumentException();
                        }
                        daysOfTheMonth.add(dayOfTheMonth);
                    }
                } catch (IllegalArgumentException e) {
                    ConsolePrinter.error("Invalid day of month.");
                    return null;
                }
                return new MonthlyPattern(daysOfTheMonth);
            }

            default -> {
                return null;
            }
        }
    };

    private static Time askTime(String prompt){
        String input = ask(prompt);
        if (input.isBlank()) return null;
        try {
            LocalTime time = LocalTime.parse(input,DateTimeFormatter.ofPattern("HH:mm"));
            return Time.valueOf(time);
        } catch (DateTimeParseException e) {
            ConsolePrinter.error("Invalid time, expected HH:mm (24-hour format).");
            return null;
        }
    }

    private static DateRange askDateRange() {
        Date startDate = askDate("Start date (yyyy-mm-dd): ");
        if (startDate == null) return null;
        
        Date endDate = askDate("End date (yyyy-mm-dd): ");
        if (endDate == null) return null;

        if (endDate.before(startDate)) {
            ConsolePrinter.error("End date cannot be before start date.");
            return null;
        }
        return new DateRange(startDate, endDate);
    }

}