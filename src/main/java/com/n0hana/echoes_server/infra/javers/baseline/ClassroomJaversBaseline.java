package com.n0hana.echoes_server.infra.javers.baseline;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.classroom.ClassroomMember;
import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.classroom.ClassroomRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClassroomJaversBaseline {

    private final ClassroomRepository classrooms;
    private final ClassroomMemberRepository members;
    private final JaversBaselineSupport support;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        support.baseline("turmas", (Iterable<ClassroomModel>) () -> classrooms.findAll().iterator(), ClassroomModel::getId);
        support.baseline("membros de turma", (Iterable<ClassroomMember>) () -> members.findAll().iterator(), ClassroomMember::getId);
    }
}
