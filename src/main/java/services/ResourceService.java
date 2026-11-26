package services;

import database.DatabaseGateway;
import database.ResourceRow;
import domain.Resource;
import domain.ResourceType;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class ResourceService {
    private final DatabaseGateway db;

    // returns false if a resource with this name already exists
    public boolean addResource(String name, ResourceType type) throws SQLException {
        if (name == null || name.isBlank() || type == null) {
            throw new IllegalArgumentException("Resource name and type are required.");
        }
        return db.addResource(name.trim(), type.name());
    }

    public List<Resource> getAllResources() throws SQLException {
        List<Resource> resources = new ArrayList<>();
        for (ResourceRow r : db.findAllResources()) {
            resources.add(new Resource(r.id(), r.name(), ResourceType.valueOf(r.type())));
        }
        return resources;
    }
}