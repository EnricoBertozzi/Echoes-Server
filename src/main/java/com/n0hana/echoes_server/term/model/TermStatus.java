package com.n0hana.echoes_server.term.model;

public enum TermStatus {
    ARCHIVED("ARCHIVED"),
    PUBLISHED("PUBLISHED"),
    DRAFT("DRAFT"),
    IN_REVIEW("IN_REVIEW"),
    APPROVED("APPROVED");

    private String name;

    private TermStatus(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public boolean compare(TermStatus status) {
      return this.name.equalsIgnoreCase(status.getName());
    }
}
