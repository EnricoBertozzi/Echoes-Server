package com.n0hana.echoes_server.classroom.material;

import org.springframework.http.HttpStatus;

public class MaterialException extends RuntimeException {
    private final HttpStatus status;
    public MaterialException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
    public HttpStatus status() { return status; }
    public static MaterialException invalid(String message) {
        return new MaterialException(HttpStatus.BAD_REQUEST, message);
    }
    public static MaterialException notFound() {
        return new MaterialException(HttpStatus.NOT_FOUND, "Material não encontrado");
    }
}
