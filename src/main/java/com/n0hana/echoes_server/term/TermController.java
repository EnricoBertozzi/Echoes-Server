package com.n0hana.echoes_server.term;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.term.dto.AcceptTermRequestDTO;
import com.n0hana.echoes_server.term.dto.CreateTermRequestDTO;
import com.n0hana.echoes_server.term.dto.TermResponseDTO;
import com.n0hana.echoes_server.term.dto.UpdateTermContentRequestDTO;
import com.n0hana.echoes_server.term.dto.UpdateTermStatusRequestDTO;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/terms")
@RequiredArgsConstructor
public class TermController {

    private final TermService termsService;
    private final UserRepository userRepository;

    /**
     * Lista o histórico completo de termos para a administração.
     * Filtros opcionais por tipo e status.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TermResponseDTO>> getAllTerms(
            @RequestParam(required = false) DocumentType type,
            @RequestParam(required = false) TermStatus status) {
        List<TermResponseDTO> terms = termsService.getAllTerms(type, status)
            .stream()
            .map(this::toDTO)
            .toList();

        return ResponseEntity.ok(terms);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<DocumentType>> getPendingTerms(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(UserNotFoundException::new);

        return ResponseEntity.ok(termsService.getPendingRequiredTerms(user.getId()));
    }

    @GetMapping("/{type}")
    public ResponseEntity<String> getLatestTerms(@PathVariable DocumentType type) {
        TermModel terms = termsService.getActiveTerms(type);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
            .header("X-Content-Type-Options", "nosniff")
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
            .orElseThrow(UserNotFoundException::new);

        boolean accepted = termsService.hasAcceptedLatestTerms(user.getId(), type);
        return ResponseEntity.ok(accepted);
    }

    @PostMapping("/accept")
    public ResponseEntity<TermResponseDTO> acceptTerms(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AcceptTermRequestDTO dto) {
        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(UserNotFoundException::new);

        UserTermAcceptance acceptance = termsService.acceptTerms(user.getId(), dto.type());
        return ResponseEntity.ok(toDTO(acceptance.getTerm()));
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revokeConsent(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(UserNotFoundException::new);

        termsService.revoke(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TermResponseDTO> createTerm(
            @Valid @RequestBody CreateTermRequestDTO dto) {
        TermModel created = termsService.createTerm(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(created));
    }

    @PatchMapping("/{id}/content")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TermResponseDTO> updateTermContent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTermContentRequestDTO dto) {
        TermModel updated = termsService.updateTermContent(id, dto.content());
        return ResponseEntity.ok(toDTO(updated));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TermResponseDTO> updateTermStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTermStatusRequestDTO dto) {
        TermModel updated = termsService.updateTermStatus(id, dto.status());
        return ResponseEntity.ok(toDTO(updated));
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
