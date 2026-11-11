package domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.sql.Time;
import java.util.Date;

@AllArgsConstructor
@Getter
@Setter
public class Availability {
    private Professional professional;
    private Date date;
    private Time start;
    private Time end;
}