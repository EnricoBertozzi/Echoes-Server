package com.n0hana.echoes_server.infra.notifier.message;

import org.springframework.web.util.HtmlUtils;

import com.n0hana.echoes_server.infra.notifier.data.ClassroomTeacherAddedData;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


//  Classe que implementa EmailMessage para avisar o professor que ele foi adicionado a uma turma
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClassroomTeacherAddedEmailMessage implements EmailMessage<ClassroomTeacherAddedData> {
    private ClassroomTeacherAddedData data;

    @Override
    public ClassroomTeacherAddedData getDate() {
        return this.data;
    }

    @Override
    public String getTitle() {
        return "Echoes - Você foi adicionado à turma %s".formatted(data.getClassroomTitle());
    }

    @Override
    public String getContent() {
        return """
                <html>
                    <body>
                        <h1>Nova turma</h1>
                        <p>
                            Você foi adicionado à turma <strong>%s</strong> (período %s).
                        </p>
                        <p>
                            Acesse o Echoes para ver a turma e gerar o código de entrada dos alunos.
                        </p>
                    </body>
                </html>
                """
                .formatted(HtmlUtils.htmlEscape(data.getClassroomTitle()), HtmlUtils.htmlEscape(data.getPeriod()));
    }
}
