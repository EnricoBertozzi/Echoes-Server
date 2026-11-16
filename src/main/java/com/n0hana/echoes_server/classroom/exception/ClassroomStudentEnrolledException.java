package com.n0hana.echoes_server.classroom.exception;

public class ClassroomStudentEnrolledException extends RuntimeException {
    public ClassroomStudentEnrolledException() {
        super("O aluno já está em outra turma");
    }
}
