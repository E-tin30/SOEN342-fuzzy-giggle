package domain;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

@Getter
public class Service {

    private final String name;
    private final boolean requiresReferral;
    private final int durationMinutes;
    private final List<Resource> requiredResources = new ArrayList<>();

    public Service(String name, boolean requiresReferral, int durationMinutes){
        if(durationMinutes <= 0){
            throw new IllegalArgumentException("Duration must be positive");
        }
        this.name = name;
        this.requiresReferral = requiresReferral;
        this.durationMinutes = durationMinutes;
    }

    public void addRequiredResource(Resource resource){
        if(resource == null){
            throw new IllegalArgumentException("Resource can't be null");
        }
        requiredResources.add(resource);
    }

    public boolean requiresReferral(){
        return requiresReferral;
    }

    @Override
    public boolean equals(Object obj){
        if(!(obj instanceof Service service)){
            return false;
        }
        return (this.name.equals(service.name));
    }
}
