package com.n0hana.echoes_server.service.password;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import lombok.Data;

@Repository
public class InMemoryPasswordCodeRepository {

    private Map<String, PasswordCode> storage = new ConcurrentHashMap<>();

    @Data
    public static class PasswordCode
    { 
        private String email;
        private String code;
        private Instant expiredAt;
        private CodeType type;
    }

    public enum CodeType {
        CHANGE("change"),
        RESET("reset"),
        REACTIVATE("reactivate");

        private String type;

        private CodeType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    public void save(PasswordCode code) {
        storage.put(code.getEmail(), code);
    }

    public void delete(String email) {
        storage.remove(email);
    }

    public PasswordCode getCode(String email) {
        return storage.get(email);

    }


}
