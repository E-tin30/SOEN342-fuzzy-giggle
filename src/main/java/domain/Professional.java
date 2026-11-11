package domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Professional {
    private int id;
    private String first_name;
    private String last_name;
    private String title;
    private String specialization;
}