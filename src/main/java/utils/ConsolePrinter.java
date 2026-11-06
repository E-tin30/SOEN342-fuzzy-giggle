package utils;

public class ConsolePrinter {

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
                7. Exit
                Enter the number of the option you want and press enter: 
                """);
    }

}
