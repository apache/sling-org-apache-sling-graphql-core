package org.apache.sling.graphql.core.mocks;

public class TypeSlingResourceDTO {
    private final String path;
    private final String resourceType;

    public TypeSlingResourceDTO(String path, String resourceType) {
        this.path = path;
        this.resourceType = resourceType;
    }

    public String getPath() {
        return path;
    }

    public String getResourceType() {
        return resourceType;
    }
}
