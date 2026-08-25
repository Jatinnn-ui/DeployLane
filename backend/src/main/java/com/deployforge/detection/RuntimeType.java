package com.deployforge.detection;

/** The language runtime a project needs, which selects the base images used for build and run. */
public enum RuntimeType {
    NODE("Node.js"),
    JAVA("Java"),
    PYTHON("Python"),
    STATIC("Static"),
    DOCKER("Docker"),
    UNKNOWN("Unknown");

    private final String displayName;

    RuntimeType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
