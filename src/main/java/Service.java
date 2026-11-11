public class Service {

    private final String name;
    private final boolean requiresReferral;

    public Service(String name, boolean requiresReferral){
        this.name = name;
        this.requiresReferral = requiresReferral;
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
