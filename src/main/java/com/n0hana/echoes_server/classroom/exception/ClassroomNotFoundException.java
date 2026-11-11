package com.n0hana.echoes_server.classroom.exception;

public class ClassroomNotFoundException extends RuntimeException {
    public ClassroomNotFoundException() {
        super("Turma não encontrada");
    }
}
