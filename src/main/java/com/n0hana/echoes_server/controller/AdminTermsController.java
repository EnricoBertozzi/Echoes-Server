package com.n0hana.echoes_server.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.dto.CreateTermsRequestDTO;
import com.n0hana.echoes_server.dto.TermsResponseDTO;
import com.n0hana.echoes_server.model.Terms;
import com.n0hana.echoes_server.service.TermsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/terms")
@RequiredArgsConstructor
public class AdminTermsController {

    private final TermsService termsService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<TermsResponseDTO>> getAllTerms(
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TermsResponseDTO> result = termsService.getAllTerms(pageable)
            .map(terms -> new TermsResponseDTO(
                terms.getId(),
                terms.getVersion(),
                terms.getContent(),
                terms.getType(),
                terms.isActive(),
                terms.getTimestamp()
            ));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TermsResponseDTO> getTermById(@PathVariable Long id) {
        Terms terms = termsService.getTermById(id);
        return ResponseEntity.ok(new TermsResponseDTO(
            terms.getId(),
            terms.getVersion(),
            terms.getContent(),
            terms.getType(),
            terms.isActive(),
            terms.getTimestamp()
        ));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TermsResponseDTO> createTerm(@RequestBody CreateTermsRequestDTO dto) {
        Terms terms = termsService.createTerm(dto);
        return ResponseEntity.ok(new TermsResponseDTO(
            terms.getId(),
            terms.getVersion(),
            terms.getContent(),
            terms.getType(),
            terms.isActive(),
            terms.getTimestamp()
        ));
    }
}
