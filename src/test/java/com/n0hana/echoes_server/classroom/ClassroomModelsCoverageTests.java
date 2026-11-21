package com.n0hana.echoes_server.classroom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.UserRole;

class ClassroomModelsCoverageTests {

    @Test
    @DisplayName("Enum de papéis expõe MODERATOR e MEMBER")
    void memberRoleValues() {
        assertArrayEquals(new ClassroomMemberRole[] { ClassroomMemberRole.MODERATOR, ClassroomMemberRole.MEMBER },
                ClassroomMemberRole.values());
        assertSame(ClassroomMemberRole.MEMBER, ClassroomMemberRole.valueOf("MEMBER"));
    }

    @Test
    @DisplayName("Info.from copia os campos da turma, com e sem código")
    void infoFromModel() {
        UUID institutionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        ClassroomModel model = ClassroomModel.builder().id(UUID.randomUUID()).title("T").description("D")
                .period("2026/02").archived(true).createdAt(now)
                .institution(InstitutionModel.builder().id(institutionId).build()).build();

        ClassroomDTO.Info plain = ClassroomDTO.Info.from(model);
        ClassroomDTO.Info withCode = ClassroomDTO.Info.from(model, "ABC234");

        assertEquals(model.getId(), plain.id());
        assertEquals(institutionId, plain.institutionId());
        assertEquals(true, plain.archived());
        assertEquals(now, plain.createdAt());
        assertNull(plain.joinCode());
        assertEquals("ABC234", withCode.joinCode());
    }

    @Test
    @DisplayName("MemberInfo.withoutEmail remove só o e-mail")
    void memberInfoWithoutEmail() {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setName("Ana");
        student.setEmail("ana@exemplo.test");
        LocalDateTime joined = LocalDateTime.now();
        ClassroomMember member = ClassroomMember.builder().user(student).joinedAt(joined).build();

        ClassroomDTO.MemberInfo info = ClassroomDTO.MemberInfo.from(member);
        ClassroomDTO.MemberInfo hidden = info.withoutEmail();

        assertEquals("ana@exemplo.test", info.email());
        assertNull(hidden.email());
        assertEquals("Ana", hidden.name());
        assertEquals(UserRole.STUDENT, hidden.role());
        assertEquals(joined, hidden.joinedAt());
        assertEquals(student.getId(), hidden.userId());
    }

    @Test
    @DisplayName("DTOs de requisição guardam os valores")
    void requestRecords() {
        assertEquals("ABC234", new ClassroomDTO.JoinRequest("ABC234").code());
        assertEquals(30L, new ClassroomDTO.CodeResponse("X", 30L).expiresInSeconds());
        assertEquals("2026/01", new ClassroomDTO.Request("T", null, "2026/01").period());
        new ClassroomDTO();
    }
}
