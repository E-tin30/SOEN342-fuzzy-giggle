package services;

import database.CalendarAppointment;
import lombok.RequiredArgsConstructor;
import utils.CalendarGateway;

import java.util.List;

@RequiredArgsConstructor
public class CalendarExportService {

    private final CalendarGateway gateway;

    public byte[] export(String calendarName, List<CalendarAppointment> appointments) {
        return gateway.generate(calendarName, appointments);
    }
}
