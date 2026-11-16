package domain;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class Professional {
    private final int id;
    private final String firstName;
    private final String lastName;
    private final String title;
    private final String specialization;
    private final List<Service> offeredServices = new ArrayList<>();
    private final List<Availability> availabilities = new ArrayList<>();

    // add availability to a professional
    public void addAvailability(Availability availability){
        if(availability == null){
            throw new IllegalArgumentException("Availability can't be null");
        }
        this.availabilities.add(availability);
    }

    // add a service that professional offers
    public void addService(Service service){
        if (service == null){
            throw new IllegalArgumentException("Service can't be null");
        }
        this.offeredServices.add(service);
    }

    // check if professional offers that service
    public boolean offersService(Service service){
        if (service == null){
            throw new IllegalArgumentException("Service can't be null");
        }
        return this.offeredServices.contains(service);
    }

    // get availabilities within the date range (null for no range) that still have room for the needed minutes
    public List<Availability> getAvailabilities(DateRange range, int minutes){
        List<Availability> open = new ArrayList<>();
        for(Availability availability : this.availabilities){
            if(availability.isWithin(range) && availability.firstFit(minutes) != null){
                open.add(availability);
            }
        }
        return open;
    }
}
