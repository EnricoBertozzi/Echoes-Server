package com.n0hana.echoes_server.term;

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

import com.n0hana.echoes_server.term.dto.AcceptTermRequestDTO;
import com.n0hana.echoes_server.term.dto.TermResponseDTO;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/terms")
@RequiredArgsConstructor
public class TermController {

    private final TermService termsService;
    private final UserRepository userRepository;

    @GetMapping("/{type}")
    public ResponseEntity<String> getLatestTerms(@PathVariable DocumentType type) {
        TermModel terms = termsService.getActiveTerms(type);
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(terms.getContent());
    }

    @GetMapping("/{type}/json")
    public ResponseEntity<TermResponseDTO> getLatestTermsJson(@PathVariable DocumentType type) {
        TermModel terms = termsService.getActiveTerms(type);
        return ResponseEntity.ok(toDTO(terms));
    }

    @GetMapping("/{type}/accepted")
    public ResponseEntity<Boolean> hasAcceptedLatestTerms(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable DocumentType type) {
        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        boolean accepted = termsService.hasAcceptedLatestTerms(user.getId(), type);
        return ResponseEntity.ok(accepted);
    }

    @PostMapping("/accept")
    public ResponseEntity<TermResponseDTO> acceptTerms(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AcceptTermRequestDTO dto) {
        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserTermAcceptance acceptance = termsService.acceptTerms(user.getId(), dto.type());
        return ResponseEntity.ok(toDTO(acceptance.getTerm()));
    }

    private TermResponseDTO toDTO(TermModel terms) {
        return new TermResponseDTO(
            terms.getId(),
            terms.getVersion(),
            terms.getContent(),
            terms.getType(),
            terms.getStatus(),
            terms.getTimestamp()
        );
    }
}
