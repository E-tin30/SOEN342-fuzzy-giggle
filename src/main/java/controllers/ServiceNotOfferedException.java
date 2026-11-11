package controllers;

public class ServiceNotOfferedException extends RuntimeException {
    
    public ServiceNotOfferedException(String messsage){
        super(messsage);
    }
}
