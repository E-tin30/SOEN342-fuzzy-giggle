package controllers;

import domain.ResourceType;
import lombok.RequiredArgsConstructor;
import services.ResourceService;

import java.sql.SQLException;

@RequiredArgsConstructor
public class ResourceController {
    private final ResourceService resourceService;

    public boolean addResource(String name, ResourceType type) throws SQLException {
        return resourceService.addResource(name, type);
    }
}