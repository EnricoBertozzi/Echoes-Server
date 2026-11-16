package com.n0hana.echoes_server.classroom.exception;

public class ClassroomCodeActiveException extends RuntimeException {
    public ClassroomCodeActiveException() {
        super("Já existe um código ativo para esta turma");
    }
}
