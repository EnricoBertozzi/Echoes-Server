package com.n0hana.echoes_server.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.dto.CreateTermsRequestDTO;
import com.n0hana.echoes_server.dto.TwoFactorDto;
import com.n0hana.echoes_server.model.DocumentType;
import com.n0hana.echoes_server.model.Terms;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.model.UserTermsAcceptance;
import com.n0hana.echoes_server.repository.TermsRepository;
import com.n0hana.echoes_server.repository.UserRepository;
import com.n0hana.echoes_server.repository.UserTermsAcceptanceRepository;
import com.n0hana.echoes_server.service.notifier.TwoFactorNotifier;
import com.n0hana.echoes_server.service.password.InMemoryPasswordCodeRepository;
import com.n0hana.echoes_server.service.password.InMemoryPasswordCodeRepository.CodeType;
import com.n0hana.echoes_server.service.password.InMemoryPasswordCodeRepository.PasswordCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TermsService {

    private final TermsRepository termsRepository;
    private final UserTermsAcceptanceRepository userTermsAcceptanceRepository;
    private final UserRepository userRepository;
    private final InMemoryPasswordCodeRepository codeRepository;
    private final TwoFactorNotifier twoFactorNotifier;

    public Terms getActiveTerms(DocumentType type) {
        return termsRepository.findByTypeAndActiveTrue(type)
            .orElseThrow(() -> new RuntimeException("No active terms found for type: " + type));
    }

    public Terms getLatestTerms(DocumentType type) {
        return termsRepository.findTopByTypeOrderByTimestampDesc(type)
            .orElseThrow(() -> new RuntimeException("No terms found for type: " + type));
    }

    public boolean hasAcceptedLatestTerms(UUID userId, DocumentType type) {
        return userTermsAcceptanceRepository.findLatestByUserIdAndType(userId, type)
            .map(acceptance -> {
                Terms latestTerms = getLatestTerms(type);
                return acceptance.getTerms().getId() == latestTerms.getId();
            })
            .orElse(false);
    }

    public UserTermsAcceptance acceptTerms(UUID userId, DocumentType type) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Terms terms = getActiveTerms(type);
        
        UserTermsAcceptance acceptance = new UserTermsAcceptance(user, terms);
        return userTermsAcceptanceRepository.save(acceptance);
    }

    public List<UserTermsAcceptance> getUserAcceptances(UUID userId) {
        return userTermsAcceptanceRepository.findAll().stream()
            .filter(a -> a.getUser().getId().equals(userId))
            .toList();
    }

    public void requestReactivate(String email) {
        User user = userRepository.findUserByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.isActive()) {
            throw new RuntimeException("User account is already active");
        }

        String code = generateRandomCode();
        PasswordCode codeObj = new PasswordCode();
        Instant expiresAt = Instant.now().plusSeconds(300);
        codeObj.setEmail(email);
        codeObj.setCode(code);
        codeObj.setExpiredAt(expiresAt);
        codeObj.setType(CodeType.REACTIVATE);
        codeRepository.save(codeObj);

        TwoFactorDto dto = new TwoFactorDto(email, code, expiresAt);
        twoFactorNotifier.send(dto);
    }

    public void reactivate(String email, String code) {
        PasswordCode savedCode = codeRepository.getCode(email);
        if (savedCode == null || !savedCode.getCode().equals(code)) {
            throw new RuntimeException("Invalid code");
        }
        if (savedCode.getExpiredAt().isBefore(Instant.now())) {
            throw new RuntimeException("Code expired");
        }
        if (!savedCode.getType().equals(CodeType.REACTIVATE)) {
            throw new RuntimeException("Invalid code type");
        }

        User user = userRepository.findUserByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        for (DocumentType type : DocumentType.values()) {
            acceptTerms(user.getId(), type);
        }
        user.setActive(true);
        userRepository.save(user);
        codeRepository.delete(email);
    }

    public Page<Terms> getAllTerms(Pageable pageable) {
        return termsRepository.findAll(pageable);
    }

    public Terms getTermById(Long id) {
        return termsRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Termo não encontrado"));
    }

    @Transactional
    public Terms createTerm(CreateTermsRequestDTO dto) {
        termsRepository.findByTypeAndActiveTrue(dto.type()).ifPresent(active -> {
            if (!isVersionGreater(dto.version(), active.getVersion())) {
                throw new RuntimeException(
                    "Versão deve ser maior que a atual (" + active.getVersion() + ")"
                );
            }
            active.setActive(false);
            termsRepository.save(active);
        });

        Terms terms = new Terms();
        terms.setVersion(dto.version());
        terms.setContent(dto.content());
        terms.setType(dto.type());
        terms.setActive(true);
        return termsRepository.save(terms);
    }

    private boolean isVersionGreater(String newVersion, String currentVersion) {
        int[] newParts = parseVersion(newVersion);
        int[] currentParts = parseVersion(currentVersion);
        for (int i = 0; i < 3; i++) {
            if (newParts[i] > currentParts[i]) return true;
            if (newParts[i] < currentParts[i]) return false;
        }
        return false;
    }

    private int[] parseVersion(String version) {
        try {
            int[] parts = Arrays.stream(version.split("\\."))
                .mapToInt(Integer::parseInt)
                .toArray();
            if (parts.length != 3) {
                throw new RuntimeException("Formato de versão inválido. Use major.minor.patch (ex: 2.0.0)");
            }
            return parts;
        } catch (NumberFormatException e) {
            throw new RuntimeException("Formato de versão inválido. Use major.minor.patch (ex: 2.0.0)");
        }
    }

    @Transactional
    public void revoke(User user) {
        userTermsAcceptanceRepository.deleteAll(user.getId());
        user.setActive(false);
        userRepository.save(user);
    }

    private String generateRandomCode() {
        int code = 100000 + (int)(Math.random() * 900000);
        return String.valueOf(code);
    }
}
