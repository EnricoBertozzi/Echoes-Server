package com.n0hana.echoes_server.classroom;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.infra.redis.CodeType;
import com.n0hana.echoes_server.infra.redis.RedisCodeStore;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClassroomCodeService {

    // sem I, O, 0 e 1 para não confundir na hora de digitar
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 10;

    private final RedisCodeStore store;
    private final SecureRandom random = new SecureRandom();

    // grava o código e o índice reverso, os dois com o mesmo tempo de vida
    public String generate(UUID classroomId) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String code = randomCode();
            if (!store.isExists(code, CodeType.CLASSROOM_JOIN)) {
                store.save(code, classroomId.toString(), CodeType.CLASSROOM_JOIN);
                store.save(classroomId.toString(), code, CodeType.CLASSROOM_JOIN_BY_ID);
                return code;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código único para a turma");
    }

    // código ativo da turma, vazio quando já expirou
    public Optional<String> findCode(UUID classroomId) {
        return store.get(classroomId.toString(), CodeType.CLASSROOM_JOIN_BY_ID);
    }

    public Optional<UUID> findClassroomId(String code) {
        return store.get(code, CodeType.CLASSROOM_JOIN).map(UUID::fromString);
    }

    public long secondsLeft(String code) {
        return store.getExpire(code, CodeType.CLASSROOM_JOIN);
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
