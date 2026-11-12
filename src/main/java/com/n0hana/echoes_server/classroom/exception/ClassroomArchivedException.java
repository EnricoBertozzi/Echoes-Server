package com.n0hana.echoes_server.classroom.exception;

public class ClassroomArchivedException extends RuntimeException {
    public ClassroomArchivedException() {
        super("Turma arquivada não pode ser alterada");
    }
}
