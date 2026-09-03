package com.n0hana.echoes_server.model;

public enum UserRole {
    STUDENT("STUDENT"),
    TEACHER("TEACHER"),
    ADMIN("ADMIN");

    private String name;

    private UserRole(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }

    public boolean compare(UserRole role) {
        return this.name.equalsIgnoreCase(role.getName());
    }

}
