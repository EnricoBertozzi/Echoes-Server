package com.n0hana.echoes_server.term;

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
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.term.dto.AcceptTermRequestDTO;
import com.n0hana.echoes_server.term.dto.CreateTermRequestDTO;
import com.n0hana.echoes_server.term.dto.TermResponseDTO;
import com.n0hana.echoes_server.term.dto.UpdateTermStatusRequestDTO;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.User;

import org.springframework.http.HttpStatus;

import jakarta.validation.Valid;
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
            .orElseThrow(UserNotFoundException::new);

        boolean accepted = termsService.hasAcceptedLatestTerms(user.getId(), type);
        return ResponseEntity.ok(accepted);
    }

    @PostMapping("/accept")
    public ResponseEntity<TermResponseDTO> acceptTerms(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AcceptTermRequestDTO dto) {
        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(UserNotFoundException::new);

        UserTermAcceptance acceptance = termsService.acceptTerms(user.getId(), dto.type());
        return ResponseEntity.ok(toDTO(acceptance.getTerm()));
    }

    /**
     * Cria um novo termo (conteúdo e versão imutáveis após criação).
     * <p>
     * Restrito ao ADMIN. Termos não podem ser deletados.
     * <ul>
     *   <li>Se criado com status {@code PUBLISHED}, aplica a mesma regra de publicação: o termo
     *       publicado anterior do mesmo tipo é arquivado automaticamente e a versão deve ser maior
     *       que a atualmente publicada (quando existir).</li>
     *   <li>Se criado com outro status (ex.: {@code DRAFT}, {@code IN_REVIEW}), o termo fica
     *       disponível para revisão e publicação posterior via {@code PATCH /terms/{id}/status}.</li>
     *   <li>Se {@code status} não for informado, o padrão é {@code DRAFT}.</li>
     * </ul>
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TermResponseDTO> createTerm(
            @Valid @RequestBody CreateTermRequestDTO dto) {
        TermModel created = termsService.createTerm(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(created));
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
