package utils;

import java.io.IOException;

public class CalendarExportException extends RuntimeException {
    public CalendarExportException(String message, IOException e) {
        super(message);
    }
}
