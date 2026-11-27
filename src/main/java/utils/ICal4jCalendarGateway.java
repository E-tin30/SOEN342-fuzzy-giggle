package utils;

import database.CalendarAppointment;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.XProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.List;

public class ICal4jCalendarGateway implements CalendarGateway {

    private static final ZoneId ZONE = ZoneId.of("America/Toronto");

    @Override
    public byte[] generate(String calendarName, List<CalendarAppointment> appointments) {
        Calendar calendar = new Calendar()
                .withProdId("-//Clinic//Appointments//EN")
                .withDefaults()
                .withProperty(new XProperty("X-WR-CALNAME", calendarName))
                .getFluentTarget();

        for (CalendarAppointment a : appointments) {
            VEvent event = new VEvent(
                    a.start().atZone(ZONE).toInstant(),
                    a.end().atZone(ZONE).toInstant(),
                    "Appointment with " + a.professional());
            event.add(new Uid("appt-" + a.id() + "@yourclinic.ca"));
            calendar.add(event);
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new CalendarOutputter().output(calendar, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new CalendarExportException("Could not generate .ics file", e);
        }
    }
}
