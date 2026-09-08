package com.n0hana.echoes_server.user;

public enum UserRole {
    STUDENT("STUDENT"),
    TEACHER("TEACHER"),
    ADMIN("ADMIN");

    private String role;

    private UserRole(String role) {
        this.role = role;
    }
    
    public String getRole() {
        return role;
    }

    public boolean compare(UserRole role) {
        return this.role.equalsIgnoreCase(role.getRole());
    }

}
