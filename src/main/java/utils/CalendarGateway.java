package utils;

import database.CalendarAppointment;

import java.util.List;

public interface CalendarGateway {
    byte[] generate(String calendarName, List<CalendarAppointment> appointments);
}