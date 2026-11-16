package domain;

public class Service {

    private final String name;
    private final boolean requiresReferral;
    private final int durationMinutes;

    public Service(String name, boolean requiresReferral, int durationMinutes){
        if(durationMinutes <= 0){
            throw new IllegalArgumentException("Duration must be positive");
        }
        this.name = name;
        this.requiresReferral = requiresReferral;
        this.durationMinutes = durationMinutes;
    }

    public int getDurationMinutes(){
        return durationMinutes;
    }

    public boolean requiresReferral(){
        return requiresReferral;
    }

    public String getName(){
        return name;
    }

    @Override
    public boolean equals(Object obj){
        if(!(obj instanceof Service service)){
            return false;
        }
        return (this.name.equals(service.name));
    }
}
