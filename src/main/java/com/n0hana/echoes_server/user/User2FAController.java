package com.n0hana.echoes_server.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.user.dto.CompleteRegistrationDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class User2FAController {

    private final UserService service;

    @PostMapping("/2fa")
    public ResponseEntity<UserDTO> completeRegistration(@RequestBody @Valid CompleteRegistrationDTO dto) {
        return ResponseEntity.ok(service.completeRegistration(dto));
    }
}
