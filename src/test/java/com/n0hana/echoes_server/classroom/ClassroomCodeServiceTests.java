package com.n0hana.echoes_server.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.infra.redis.CodeType;
import com.n0hana.echoes_server.infra.redis.RedisCodeStore;

@ExtendWith(MockitoExtension.class)
class ClassroomCodeServiceTests {

    @Mock
    private RedisCodeStore store;

    @InjectMocks
    private ClassroomCodeService service;

    @Test
    @DisplayName("Gera código de 6 caracteres e grava o código e o índice reverso")
    void generatesCodeAndSavesBothKeys() {
        UUID classroomId = UUID.randomUUID();
        when(store.isExists(anyString(), eq(CodeType.CLASSROOM_JOIN))).thenReturn(false);

        String code = service.generate(classroomId);

        assertTrue(code.matches("[A-HJ-NP-Z2-9]{6}"));
        verify(store).save(code, classroomId.toString(), CodeType.CLASSROOM_JOIN);
        verify(store).save(classroomId.toString(), code, CodeType.CLASSROOM_JOIN_BY_ID);
    }

    @Test
    @DisplayName("Tenta de novo quando o código já existe")
    void retriesWhenCodeAlreadyExists() {
        when(store.isExists(anyString(), eq(CodeType.CLASSROOM_JOIN))).thenReturn(true, false);

        service.generate(UUID.randomUUID());

        verify(store, times(2)).isExists(anyString(), eq(CodeType.CLASSROOM_JOIN));
    }

    @Test
    @DisplayName("Falha depois de 10 tentativas sem código livre")
    void failsAfterMaxAttempts() {
        when(store.isExists(anyString(), eq(CodeType.CLASSROOM_JOIN))).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.generate(UUID.randomUUID()));

        verify(store, times(10)).isExists(anyString(), eq(CodeType.CLASSROOM_JOIN));
        verify(store, never()).save(anyString(), anyString(), eq(CodeType.CLASSROOM_JOIN));
    }

    @Test
    @DisplayName("Busca o código ativo da turma")
    void findsCodeByClassroom() {
        UUID classroomId = UUID.randomUUID();
        when(store.get(classroomId.toString(), CodeType.CLASSROOM_JOIN_BY_ID)).thenReturn(Optional.of("ABC234"));

        assertEquals(Optional.of("ABC234"), service.findCode(classroomId));
    }

    @Test
    @DisplayName("Busca a turma pelo código")
    void findsClassroomByCode() {
        UUID classroomId = UUID.randomUUID();
        when(store.get("ABC234", CodeType.CLASSROOM_JOIN)).thenReturn(Optional.of(classroomId.toString()));

        assertEquals(Optional.of(classroomId), service.findClassroomId("ABC234"));
    }

    @Test
    @DisplayName("Retorna os segundos que faltam para o código expirar")
    void returnsSecondsLeft() {
        when(store.getExpire("ABC234", CodeType.CLASSROOM_JOIN)).thenReturn(900L);

        assertEquals(900L, service.secondsLeft("ABC234"));
    }
}
