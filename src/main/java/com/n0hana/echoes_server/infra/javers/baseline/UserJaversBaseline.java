package com.n0hana.echoes_server.infra.javers.baseline;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserJaversBaseline {

    private final UserRepository repository;
    private final JaversBaselineSupport support;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        support.baseline("usuários", (Iterable<User>) () -> repository.findAll().iterator(), User::getId);
    }
}
