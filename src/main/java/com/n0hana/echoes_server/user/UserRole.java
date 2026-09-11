package com.n0hana.echoes_server.user;

public enum UserRole {
    STUDENT("STUDENT"),
    TEACHER("TEACHER"),
    MANAGER("MANAGER"),
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
