package controllers;

import database.CalendarAppointment;
import lombok.RequiredArgsConstructor;
import services.CalendarExportService;

import java.util.List;

@RequiredArgsConstructor
public class CalendarExportController {

    private final CalendarExportService service;


    public byte[] exportCalendar(String calendarName, List<CalendarAppointment> appointments) {
        return service.export(calendarName, appointments);
    }
}
