package controllers;

public class ReferralRequiredException extends RuntimeException {
    
    public ReferralRequiredException(String messsage){
        super(messsage);
    }
}
