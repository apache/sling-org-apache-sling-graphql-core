package org.apache.sling.graphql.core.mocks;

public class TypeTestDTO {
    private final boolean test;
    private final boolean boolValue;
    private final String resourcePath;
    private final String testingArgument;

    public TypeTestDTO(boolean test, boolean boolValue, String resourcePath, String testingArgument) {
        this.test = test;
        this.boolValue = boolValue;
        this.resourcePath = resourcePath;
        this.testingArgument = testingArgument;
    }

    public boolean isTest() {
        return test;
    }

    public boolean isBoolValue() {
        return boolValue;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String getTestingArgument() {
        return testingArgument;
    }

}
