package com.n0hana.echoes_server.model;

public enum DocumentType {
    TERMS_OF_USE("TERMS_OF_USE"),
    PRIVACY_POLICY("PRIVACY_POLICY"),
    DATA_DELETION_POLICY("DATA_DELETION_POLICY"),
    MARKETING_CONSENT("MARKETING_CONSENT"),
    COOKIES_POLICY("COOKIES_POLICY");

    private String name;

    private DocumentType(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public boolean compare(DocumentType type) {
      return this.name.equalsIgnoreCase(type.getName());
    }
}
