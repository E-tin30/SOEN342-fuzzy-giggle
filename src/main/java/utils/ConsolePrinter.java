package utils;

import domain.Appointment;
import domain.Availability;
import domain.Patient;
import domain.Professional;
import domain.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ConsolePrinter {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static void greeting() {
        System.out.println("""
                Welcome to the Appointment Tracking System!
                """);
    }

    public static void goodbye() {
        System.out.println("""
                
                Thanks for using the Appointment Tracking System and have a nice day!
                """);
    }

    public static void error(String message) {
        System.out.println("\n" + message + "\n");
    }

    public static void printMainMenu() {
        System.out.print("""
                
                What would you like to do?
                1. Search for a patient
                2. Book an appointment
                3. Search for availability
                4. Make availability for a professional
                5. Import availabilities from CSV
                6. Export availabilities from CSV
                7. Register a patient
                8. Register a professional
                9. Exit
                Enter the number of the option you want and press enter: 
                """);
    }

    public static void printSearchCriterion(){
        System.out.print("""
                
                What would you like to search by?
                1. First name (partial is fine)
                2. Last name (partial is fine)
                3. Insurance (RAMQ) number
                4. Phone number
                5. Date of birth
                6. Back to main menu
                Enter the number of the option you want and press enter: 
                """);
    }

    // printing helpers

    private static String hm(LocalTime time) {
        return time.format(TIME_FORMAT);
    }

    private static String day(LocalDate date) {
        return date.toString();
    }

    private static String name(Professional p) {
        return p.getTitle() + " " + p.getFirstName() + " " + p.getLastName();
    }

    public static void printPatient(Patient p) {
        System.out.println(p.getFirstName() + " " + p.getLastName()
                + " | " + p.getPhoneNumber()
                + " | RAMQ: " + (p.getRamq() == null ? "-" : p.getRamq()));
    }

    public static void printPatients(List<Patient> patients) {
        if (patients.isEmpty()) {
            System.out.println("\nNo patients found.\n");
            return;
        }
        System.out.println();
        for (int i = 0; i < patients.size(); i++) {
            System.out.print((i + 1) + ". ");
            printPatient(patients.get(i));
        }
        System.out.println();
    }

    public static void printServices(List<Service> services) {
        System.out.println();
        for (int i = 0; i < services.size(); i++) {
            Service s = services.get(i);
            System.out.println((i + 1) + ". " + s.getName()
                    + " - " + s.getDurationMinutes() + " min"
                    + (s.requiresReferral() ? " (referral required)" : ""));
        }
        System.out.println();
    }

    public static void printProfessionals(List<Professional> professionals) {
        System.out.println();
        for (int i = 0; i < professionals.size(); i++) {
            Professional p = professionals.get(i);
            System.out.println((i + 1) + ". " + name(p) + " (" + p.getSpecialization() + ")");
        }
        System.out.println();
    }

    public static void printAvailabilities(List<Availability> availabilities, int minutes) {
        System.out.println();
        for (int i = 0; i < availabilities.size(); i++) {
            Availability a = availabilities.get(i);
            System.out.println((i + 1) + ". " + name(a.getProfessional())
                    + " (" + a.getProfessional().getSpecialization() + ")"
                    + " | " + day(a.getDate())
                    + " | " + hm(a.getStart()) + " - " + hm(a.getEnd())
                    + " | earliest start: " + hm(a.firstFit(minutes)));
            for (Appointment booked : a.getAppointments()) {
                System.out.println("      already booked: " + hm(booked.getStart()) + " - " + hm(booked.getEnd()));
            }
        }
        System.out.println();
    }

    public static void printAppointment(Appointment a) {
        System.out.println("\nAppointment requested (" + a.getStatus() + ")");
        System.out.println("  Patient:      " + a.getPatient().getFirstName() + " " + a.getPatient().getLastName());
        System.out.println("  Professional: " + name(a.getProfessional()));
        System.out.println("  Service:      " + a.getService().getName());
        System.out.println("  When:         " + day(a.getDate()) + ", " + hm(a.getStart()) + " - " + hm(a.getEnd())
                + " (" + a.getDurationMinutes() + " min)");
        System.out.println("  Clinic:       " + a.getClinic().getName() + ", " + a.getClinic().getLocation());
        if (a.getReferral() != null) {
            System.out.println("  Referral:     " + a.getReferral().getReferringPhysician());
        }
        System.out.println();
    }

}
