package domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter 
@AllArgsConstructor
public class Referral {
    private final String referringPhysician;
    private final String referralType;
    private final String requestedSpecialty;
    private final String requestedProfessional;
    private final String clinicalReason;
    private final String priority;
    private String status; // will prob switch to enum but don't have values yet
}
