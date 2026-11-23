package com.n0hana.echoes_server.infra.notifier.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

 //classe que implementa MessageData para notificação de professor  adicionado a uma turma.

@Getter
@AllArgsConstructor
public class ClassroomTeacherAddedData implements MessageData {
    private String classroomTitle;

    private String period;
}
