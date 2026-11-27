import controllers.AppointmentController;
import controllers.AvailabilityController;
import controllers.PatientController;
import controllers.ProfessionalController;
import controllers.ReferralRequiredException;
import controllers.ResourceController;
import controllers.ServiceNotOfferedException;
import controllers.WaitlistController;
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
import domain.pattern.RepetitionPattern;
import domain.Resource;
import domain.ResourceType;
import domain.Service;
import domain.WaitlistEntry;
import domain.pattern.MonthlyPattern;
import domain.pattern.WeeklyPattern;
import domain.pattern.DailyPattern;
import services.*;
import utils.ConsolePrinter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
                //updated: no longer clearing database on start
                //db.clearAll();

                Clinic clinic = new Clinic(1, "Clinic", "Montreal");
                ActivityLog activityLog = new ActivityLog();

                //services
                AppointmentService appointmentService = new AppointmentService(db);
                AvailabilityService availabilityService = new AvailabilityService(db);
                CSVService csvService = new CSVService(db);
                PatientService patientService = new PatientService(db);
                ProfessionalService professionalService = new ProfessionalService(db);
                ResourceService resourceService = new ResourceService(db);
                ServiceCatalogService serviceCatalogService = new ServiceCatalogService(db);
                SeedService seedService = new SeedService(db, patientService, csvService, serviceCatalogService, resourceService);
                WaitlistService waitlistService = new WaitlistService(db);

                //controllers
                AppointmentController appointmentController = new AppointmentController(clinic, activityLog, availabilityService, appointmentService);
                AvailabilityController availabilityController = new AvailabilityController(clinic, availabilityService);
                PatientController patientController = new PatientController(clinic, patientService);
                ProfessionalController professionalController = new ProfessionalController(professionalService, serviceCatalogService);
                ResourceController resourceController = new ResourceController(resourceService);
                WaitlistController waitlistController = new WaitlistController(clinic, waitlistService);

                ClinicLoader clinicLoader = new ClinicLoader(clinic, availabilityService, professionalService, serviceCatalogService, resourceService);

                //start by seeding database with demo data if it's empty
                seedService.seedIfEmpty();

                for (Patient p : patientService.getAllPatients()) {
                    clinic.addPatient(p);
                }

                clinicLoader.sync();

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
                                bookAppointmentFlow(clinic, appointmentController, waitlistController, null);
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
                            try {
                                clinicLoader.sync();
                                makeAvailabilityFlow(clinic, availabilityController);
                            } catch (Exception e) {
                                ConsolePrinter.error("Error while creating availability: " + e.getMessage());
                            }
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
                        case 7 -> registerPatientFlow(patientController); // register patient
                        case 8 -> registerProfessionalFlow(clinic, professionalController, clinicLoader); // register professional
                        case 9 -> addResourceFlow(resourceController, clinicLoader); // add resource
                        case 10 -> waitlistMenuFlow(clinic, appointmentController, waitlistController, clinicLoader);
                        case 11 -> { //exit
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

    private static LocalDate askDate(String prompt) {
        String in = ask(prompt);
        if (in.isEmpty()) return null;
        try {
            return LocalDate.parse(in);
        } catch (DateTimeParseException e) {
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
        LocalDate dob = null;

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

    private static boolean bookAppointmentFlow(Clinic clinic, AppointmentController controller, WaitlistController waitlistController, Patient preselected) {
        // 1. find and select the patient
        Patient patient = preselected;
        if (patient == null) {
            List<Patient> patients = searchPatientFlow(controller);
            if (patients.isEmpty()) return false;
            int p = pick("Pick the patient (number, blank to cancel): ", patients.size());
            if (p < 0) return false;
            patient = patients.get(p);
        }
        controller.selectPatient(patient);

        // 2. choose the service
        List<Service> services = clinic.getOfferedServices();
        ConsolePrinter.printServices(services);
        int s = pick("Pick the service (number, blank to cancel): ", services.size());
        if (s < 0) return false;
        Service service = services.get(s);

        int minutes = service.getDurationMinutes();

        // 3. request the appointment (may need a referral first)
        List<Availability> options;
        try {
            options = controller.requestNewAppointment(service);
        } catch (ServiceNotOfferedException e) {
            ConsolePrinter.error("This clinic does not offer that service.");
            return false;
        } catch (ReferralRequiredException e) {
            System.out.println("\nThis service requires a referral.");
            Referral referral = askReferral(service);
            if (referral == null) return false;
            options = controller.provideReferral(referral);
        }

        if (options.isEmpty()) {
            ConsolePrinter.error("No availability for that service.");
            if (preselected == null
                    && ask("Add this patient to the waitlist? (y/n): ").equalsIgnoreCase("y")) {
                addToWaitlistFlow(clinic, controller, waitlistController, patient);
            }
            return false;
        }

        // 4. choose a slot
        ConsolePrinter.printAvailabilities(options, minutes);
        int a = pick("Pick an availability (number, blank to cancel): ", options.size());
        if (a < 0) return false;
        Availability chosen = options.get(a);

        LocalTime start = chosen.firstFit(minutes);
        String in = ask("Start time (hh:mm, blank = " + start.format(DateTimeFormatter.ofPattern("HH:mm")) + "): ");
        if (!in.isEmpty()) {
            try {
                start = LocalTime.parse(in, DateTimeFormatter.ofPattern("HH:mm"));
            } catch (DateTimeParseException e) {
                ConsolePrinter.error("Invalid time, expected hh:mm.");
                return false;
            }
        }

        Appointment appointment = controller.select(chosen, start);
        if (appointment == null) {
            ConsolePrinter.error("That time doesn't work: a " + minutes + "-minute appointment must start and end inside the availability and not overlap an appointment that is already booked.");
            return false;
        }
        ConsolePrinter.printAppointment(appointment);
        return true;
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
        LocalDate from = askDate("From date (yyyy-mm-dd, blank for any): ");
        if (from != null) {
            LocalDate to = askDate("To date (yyyy-mm-dd, blank = same day): ");
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
    private static void makeAvailabilityFlow(Clinic clinic, AvailabilityController controller)
            throws java.sql.SQLException {
        Professional professional = selectProfessional(clinic);
        if (professional == null) return;

        System.out.println("""
                Select an availability type:
                1. Single date
                2. Recurring dates
                """);
        int type = pick("Selection (blank to cancel): ", 2);
        if (type < 0) return;

        LocalDate date = null;
        DateRange range = null;
        RepetitionPattern pattern = null;
        if (type == 0) {
            date = askDate("Date (yyyy-mm-dd): ");
            if (date == null) return;
        } else {
            range = askDateRange();
            if (range == null) return;
            pattern = askRepetitionPattern();
            if (pattern == null) return;
        }

        LocalTime start = askTime("Start time (HH:mm): ");
        if (start == null) return;
        LocalTime end = askTime("End time (HH:mm): ");
        if (end == null) return;

        if (type == 0) {
            boolean created = controller.createSingleAvailability(professional, date, start, end, false);
            System.out.println(created
                    ? "Availability created."
                    : "Availability was not created because it conflicts with an existing availability.");
        } else {
            int created = controller.createRecurringAvailability(professional, range, pattern, start, end);
            System.out.println("Created " + created + " recurring availabilities.");
        }
    }


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
    }

    private static LocalTime askTime(String prompt){
        String input = ask(prompt);
        if (input.isBlank()) return null;
        try {
            return LocalTime.parse(input, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            ConsolePrinter.error("Invalid time, expected HH:mm (24-hour format).");
            return null;
        }
    }

    private static DateRange askDateRange() {
        LocalDate startDate = askDate("Start date (yyyy-mm-dd): ");
        if (startDate == null) return null;
        
        LocalDate endDate = askDate("End date (yyyy-mm-dd): ");
        if (endDate == null) return null;

        if (endDate.isBefore(startDate)) {
            ConsolePrinter.error("End date cannot be before start date.");
            return null;
        }
        return new DateRange(startDate, endDate);
    }

    private static void registerPatientFlow(PatientController controller) {
        String first = ask("First name (blank to cancel): ");
        if (first.isEmpty()) return;
        String last = ask("Last name: ");
        LocalDate dob = askDate("Date of birth (yyyy-mm-dd): ");
        if (dob == null) return;
        String phone = ask("Phone number: ");
        String ramq = ask("Insurance (RAMQ) number: ");
        String insurance = ask("Insurance provider: ");

        if (last.isEmpty() || phone.isEmpty() || ramq.isEmpty() || insurance.isEmpty()) {
            ConsolePrinter.error("All fields are required.");
            return;
        }

        try {
            boolean registered = controller.registerPatient(first, last, dob, phone, ramq, insurance);
            System.out.println(registered
                    ? "Patient registered."
                    : "A patient with this RAMQ number is already registered.");
        } catch (java.sql.SQLException e) {
            ConsolePrinter.error("Could not register the patient: " + e.getMessage());
        }
        
    }

    // case 8
    private static void registerProfessionalFlow(Clinic clinic, ProfessionalController controller, ClinicLoader clinicLoader) {
        String first = ask("First name (blank to cancel): ");
        if (first.isEmpty()) return;
        String last = ask("Last name: ");
        String title = ask("Title (e.g. Dr.): ");
        String specialization = ask("Specialization: ");

        try {
            List<String> serviceNames = new ArrayList<>();

            // pick from the existing services
            List<Service> services = clinic.getOfferedServices();
            ConsolePrinter.printServices(services);
            String input = ask("Services offered (numbers separated by commas, blank = default for this specialization): ");
            if (!input.isEmpty()) {
                for (String value : input.split(",")) {
                    int number = Integer.parseInt(value.trim());
                    if (number < 1 || number > services.size()) {
                        throw new NumberFormatException();
                    }
                    serviceNames.add(services.get(number - 1).getName());
                }
            }

            // or create new ones
            while (ask("Create a new service for this professional? (y/n): ").equalsIgnoreCase("y")) {
                String name = ask("Service name: ");
                int minutes = Integer.parseInt(ask("Duration in minutes: "));
                boolean referral = ask("Requires a referral? (y/n): ").equalsIgnoreCase("y");

                List<Resource> resources = clinic.getResources();
                List<String> resourceNames = new ArrayList<>();
                if (!resources.isEmpty()) {
                    for (int i = 0; i < resources.size(); i++) {
                        System.out.println((i + 1) + ". " + resources.get(i).getName() + " (" + resources.get(i).getType() + ")");
                    }
                    String resourceInput = ask("Resources this service needs (numbers separated by commas, blank for none): ");
                    if (!resourceInput.isEmpty()) {
                        for (String value : resourceInput.split(",")) {
                            int number = Integer.parseInt(value.trim());
                            if (number < 1 || number > resources.size()) {
                                throw new NumberFormatException();
                            }
                            resourceNames.add(resources.get(number - 1).getName());
                        }
                    }
                }

                if (controller.addService(name, specialization, referral, minutes, resourceNames)) {
                    serviceNames.add(name);
                } else {
                    System.out.println("A service with that name already exists. Pick it from the list instead.");
                }
            }

            boolean registered = controller.registerProfessional(first, last, title, specialization, serviceNames);
            if (registered) {
                clinicLoader.sync();
                System.out.println("Professional registered.");
            } else {
                System.out.println("This professional is already registered.");
            }
        } catch (NumberFormatException e) {
            ConsolePrinter.error("Invalid number.");
        } catch (IllegalArgumentException e) {
            ConsolePrinter.error(e.getMessage());
        } catch (java.sql.SQLException e) {
            ConsolePrinter.error("Could not register the professional: " + e.getMessage());
        }
    }

    // case 9
    private static void addResourceFlow(ResourceController controller, ClinicLoader clinicLoader) {
        String name = ask("Resource name (blank to cancel): ");
        if (name.isEmpty()) return;

        System.out.println("""
                Select a resource type:
                1. Room
                2. Equipment
                3. Role (e.g. nurse, assistant)
                """);
        int type = pick("Selection (blank to cancel): ", 3);
        if (type < 0) return;
        ResourceType resourceType = ResourceType.values()[type];

        try {
            boolean added = controller.addResource(name, resourceType);
            if (added) {
                clinicLoader.sync();
                System.out.println("Resource added.");
            } else {
                System.out.println("A resource with this name already exists.");
            }
        } catch (IllegalArgumentException e) {
            ConsolePrinter.error(e.getMessage());
        } catch (java.sql.SQLException e) {
            ConsolePrinter.error("Could not add the resource: " + e.getMessage());
        }
    }

    // case 10
    private static void waitlistMenuFlow(Clinic clinic, AppointmentController searchController,
                                        WaitlistController controller, ClinicLoader clinicLoader) {
        try {
            clinicLoader.sync();
            System.out.println("""
                    Waitlist:
                    1. Add a patient to the waitlist
                    2. View the waitlist
                    3. Change a priority
                    4. Remove a patient from the waitlist
                    5. Find waitlisted patients for an opening
                    """);
            int choice = pick("Selection (blank to cancel): ", 5);
            switch (choice) {
                case 0 -> addToWaitlistFlow(clinic, searchController, controller, null);
                case 1 -> ConsolePrinter.printWaitlist(controller.viewWaitlist());
                case 2 -> changePriorityFlow(controller);
                case 3 -> removeFromWaitlistFlow(controller);
                case 4 -> offerOpeningFlow(clinic, searchController, controller);
                default -> { }
            }
        } catch (Exception e) {
            ConsolePrinter.error("Error with the waitlist: " + e.getMessage());
        }
    }

    // the patient is already chosen when this comes from booking, otherwise it is searched
    private static void addToWaitlistFlow(Clinic clinic, AppointmentController searchController,
                                        WaitlistController controller, Patient preselected) {
        try {
            Patient patient = preselected;
            if (patient == null) {
                List<Patient> patients = searchPatientFlow(searchController);
                if (patients.isEmpty()) return;
                int p = pick("Pick the patient (number, blank to cancel): ", patients.size());
                if (p < 0) return;
                patient = patients.get(p);
            }

            Professional professional = null;
            List<Professional> professionals = clinic.getProfessionals();
            ConsolePrinter.printProfessionals(professionals);
            int pr = pick("Waiting for a specific professional (number, blank for any): ", professionals.size());
            if (pr >= 0) professional = professionals.get(pr);

            String specialization = null;
            if (professional == null) {
                specialization = ask("Specialization (blank to cancel): ");
                if (specialization.isEmpty()) return;
            }

            DateRange range = null;
            LocalDate from = askDate("From date (yyyy-mm-dd, blank for any): ");
            if (from != null) {
                LocalDate to = askDate("To date (yyyy-mm-dd, blank = same day): ");
                range = new DateRange(from, to == null ? from : to);
            }

            int priority = askPriority("Priority 1 (highest) to 5 (lowest), blank = 3: ", 3);
            if (priority < 0) return;

            controller.addToWaitlist(patient, professional, specialization, range, priority);
            System.out.println("Added to the waitlist.");
        } catch (IllegalArgumentException e) {
            ConsolePrinter.error(e.getMessage());
        } catch (java.sql.SQLException e) {
            ConsolePrinter.error("Could not add to the waitlist: " + e.getMessage());
        }
    }

    private static void changePriorityFlow(WaitlistController controller) throws java.sql.SQLException {
        List<WaitlistEntry> entries = controller.viewWaitlist();
        ConsolePrinter.printWaitlist(entries);
        if (entries.isEmpty()) return;
        int e = pick("Pick an entry (number, blank to cancel): ", entries.size());
        if (e < 0) return;
        int priority = askPriority("New priority 1 (highest) to 5 (lowest): ", -1);
        if (priority < 0) return;
        controller.changePriority(entries.get(e), priority);
        System.out.println("Priority updated.");
    }

    private static void removeFromWaitlistFlow(WaitlistController controller) throws java.sql.SQLException {
        List<WaitlistEntry> entries = controller.viewWaitlist();
        ConsolePrinter.printWaitlist(entries);
        if (entries.isEmpty()) return;
        int e = pick("Pick an entry to remove (number, blank to cancel): ", entries.size());
        if (e < 0) return;
        if (!ask("Remove this patient from the waitlist? (y/n): ").equalsIgnoreCase("y")) return;
        controller.removeFromWaitlist(entries.get(e));
        System.out.println("Removed from the waitlist.");
    }

    // returns the priority (1 to 5), the default when blank, or -1 when invalid
    private static int askPriority(String prompt, int defaultValue) {
        String in = ask(prompt);
        if (in.isEmpty()) return defaultValue;
        try {
            int n = Integer.parseInt(in);
            if (n >= 1 && n <= 5) return n;
        } catch (NumberFormatException ignored) { }
        ConsolePrinter.error("Priority must be a number from 1 to 5.");
        return -1;
    }

    // an opening appeared: find who is waiting for it, offer it by phone, book if accepted
    private static void offerOpeningFlow(Clinic clinic, AppointmentController appointmentController, WaitlistController controller) throws java.sql.SQLException {
        Professional professional = selectProfessional(clinic);
        if (professional == null) return;
        LocalDate date = askDate("Date of the opening (yyyy-mm-dd): ");
        if (date == null) return;

        List<WaitlistEntry> eligible = controller.findEligible(professional, date);
        if (eligible.isEmpty()) {
            System.out.println("\nNo one on the waitlist matches this opening.\n");
            return;
        }
        System.out.println("\nWaitlisted patients who match (highest priority first):");
        ConsolePrinter.printWaitlist(eligible);
        int e = pick("Offer the opening to (number, blank to cancel): ", eligible.size());
        if (e < 0) return;
        WaitlistEntry entry = eligible.get(e);

        controller.markOffered(entry);
        System.out.println("Call " + entry.getPatient().getFirstName() + " " + entry.getPatient().getLastName()
                + " at " + entry.getPatient().getPhoneNumber() + " to offer the opening.");

        if (!ask("Did the patient accept? (y/n): ").equalsIgnoreCase("y")) {
            controller.declineOffer(entry);
            System.out.println("Offer declined. The patient stays on the waitlist.");
            return;
        }

        boolean booked;
        try {
            booked = bookAppointmentFlow(clinic, appointmentController, controller, entry.getPatient());
        } catch (RuntimeException ex) {
            controller.declineOffer(entry);
            throw ex;
        }
        if (booked) {
            controller.markBooked(entry);
            System.out.println("Waitlist entry completed.");
        } else {
            controller.declineOffer(entry);
            System.out.println("No appointment was booked. The patient stays on the waitlist.");
        }
    }

}