package com.n0hana.echoes_server.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.dto.AcceptTermsRequestDTO;
import com.n0hana.echoes_server.dto.ReactivateRequestDTO;
import com.n0hana.echoes_server.dto.TermsResponseDTO;
import com.n0hana.echoes_server.model.DocumentType;
import com.n0hana.echoes_server.model.Terms;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.model.UserTermsAcceptance;
import com.n0hana.echoes_server.repository.UserRepository;
import com.n0hana.echoes_server.service.TermsService;
import com.n0hana.echoes_server.service.logs.Auditable;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class TermsController {

    private final TermsService termsService;
    private final UserRepository userRepository;

    @GetMapping("/{type}")
    public ResponseEntity<String> getLatestTerms(@PathVariable DocumentType type) {
        Terms terms = termsService.getActiveTerms(type);
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(terms.getContent());
    }

    @GetMapping("/{type}/json")
    public ResponseEntity<TermsResponseDTO> getLatestTermsJson(@PathVariable DocumentType type) {
        Terms terms = termsService.getActiveTerms(type);
        return ResponseEntity.ok(toDTO(terms));
    }

    @GetMapping("/{type}/accepted")
    public ResponseEntity<Boolean> hasAcceptedLatestTerms(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable DocumentType type) {
        User user = userRepository.findUserByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        boolean accepted = termsService.hasAcceptedLatestTerms(user.getId(), type);
        return ResponseEntity.ok(accepted);
    }

    @PostMapping("/accept")
    public ResponseEntity<TermsResponseDTO> acceptTerms(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AcceptTermsRequestDTO dto) {
        User user = userRepository.findUserByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserTermsAcceptance acceptance = termsService.acceptTerms(user.getId(), dto.type());
        return ResponseEntity.ok(toDTO(acceptance.getTerms()));
    }

    @PostMapping("/revoke")
    @Auditable(action = "Revogação de consentimento", entity = "TERMS")
    public ResponseEntity<Void> revokeConsent(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findUserByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        termsService.revoke(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reactivate/request")
    public ResponseEntity<Void> requestReactivate(@RequestBody AcceptTermsRequestDTO dto) {
        termsService.requestReactivate(dto.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reactivate")
    public ResponseEntity<Void> reactivate(@RequestBody ReactivateRequestDTO dto) {
        termsService.reactivate(dto.email(), dto.code());
        return ResponseEntity.ok().build();
    }

    private TermsResponseDTO toDTO(Terms terms) {
        return new TermsResponseDTO(
            terms.getId(),
            terms.getVersion(),
            terms.getContent(),
            terms.getType(),
            terms.isActive(),
            terms.getTimestamp()
        );
    }
}
