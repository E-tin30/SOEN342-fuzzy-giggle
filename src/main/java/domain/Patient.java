package domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@AllArgsConstructor
@Getter @Setter
public class Patient {
    private String fist_name;
    private String last_name;
    private Date date_of_birth;
    private String phone_number;
    private String ramq;
    private String insurance;
}
