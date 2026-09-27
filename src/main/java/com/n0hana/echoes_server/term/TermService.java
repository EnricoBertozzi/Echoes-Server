package com.n0hana.echoes_server.term;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.term.dto.CreateTermRequestDTO;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.RequiredTermsNotAcceptedException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TermService {

    private static final List<DocumentType> REQUIRED_DOCUMENTS = List.of(
        DocumentType.TERMS_OF_USE,
        DocumentType.PRIVACY_POLICY,
        DocumentType.COOKIES_POLICY
    );

    private final TermRepository termRepository;
    private final UserTermAcceptanceRepository userTermAcceptanceRepository;
    private final UserRepository userRepository;

    // ------------------------------------------------------------------ consultas

    public TermModel getActiveTerms(DocumentType type) {
        return termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(type, TermStatus.PUBLISHED)
            .orElseThrow(() -> new RuntimeException("No active terms found for type: " + type));
    }

    public TermModel getLatestTerms(DocumentType type) {
        return termRepository.findTopByTypeOrderByTimestampDesc(type)
            .orElseThrow(() -> new RuntimeException("No terms found for type: " + type));
    }

    public boolean hasAcceptedLatestTerms(UUID userId, DocumentType type) {
        return userTermAcceptanceRepository.findLatestByUserIdAndType(userId, type)
            .map(acceptance -> {
                TermModel latestTerms = getLatestTerms(type);
                return acceptance.getTerm().getId() == latestTerms.getId();
            })
            .orElse(false);
    }

    // ------------------------------------------------------------------ aceites

    /**
     * Valida (sem tocar no banco) que todos os termos obrigatórios estão presentes.
     * Deve ser chamado como fail-fast ANTES de criar o User.
     */
    public void validateRequiredAccepted(Collection<DocumentType> accepted) {
        if (accepted == null) {
            throw new RequiredTermsNotAcceptedException(REQUIRED_DOCUMENTS);
        }
        List<DocumentType> missing = REQUIRED_DOCUMENTS.stream()
            .filter(r -> !accepted.contains(r))
            .toList();

        if (!missing.isEmpty()) {
            log.warn("Termos obrigatórios faltando: {}", missing);
            throw new RequiredTermsNotAcceptedException(missing);
        }
        log.debug("Todos os termos obrigatórios presentes");
    }

    /**
     * Registra os aceites iniciais de um usuário recém-criado.
     * Revalida defensivamente — protege chamadas diretas.
     */
    @Transactional
    public void registerInitialAcceptances(User user, Collection<DocumentType> accepted) {
        validateRequiredAccepted(accepted);
        Set<DocumentType> unique = new LinkedHashSet<>(accepted);
        unique.forEach(type -> acceptTermsInternal(user, type));
        log.info("{} aceite(s) registrado(s) para o usuário {}", unique.size(), user.getId());
    }

    @Auditable(action = "ACCEPT_TERM", entity = "Term")
    public UserTermAcceptance acceptTerms(UUID userId, DocumentType type) {
        User user = userRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);
        log.info("Usuário {} aceitou o termo {}", userId, type);
        return acceptTermsInternal(user, type);
    }

    private UserTermAcceptance acceptTermsInternal(User user, DocumentType type) {
        TermModel terms = getActiveTerms(type);
        UserTermAcceptance saved = userTermAcceptanceRepository
            .save(new UserTermAcceptance(user, terms));
        log.debug("Aceite registrado: user={}, termId={}, version={}",
            user.getId(), terms.getId(), terms.getVersion());
        return saved;
    }

    public List<UserTermAcceptance> getUserAcceptances(UUID userId) {
        return userTermAcceptanceRepository.findAll().stream()
            .filter(a -> a.getUser().getId().equals(userId))
            .toList();
    }

    // ------------------------------------------------------------------ admin

    public Page<TermModel> getAllTerms(Pageable pageable) {
        return termRepository.findAll(pageable);
    }

    public TermModel getTermById(Long id) {
        return termRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Termo não encontrado"));
    }

    @Transactional
    public TermModel createTerm(CreateTermRequestDTO dto) {
        termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(dto.type(), TermStatus.PUBLISHED)
            .ifPresent(active -> {
                if (!isVersionGreater(dto.version(), active.getVersion())) {
                    throw new RuntimeException(
                        "Versão deve ser maior que a atual (" + active.getVersion() + ")");
                }
                active.setStatus(TermStatus.ARCHIVED);
                termRepository.save(active);
            });

        TermModel terms = new TermModel();
        terms.setVersion(dto.version());
        terms.setContent(dto.content());
        terms.setType(dto.type());
        terms.setStatus(TermStatus.PUBLISHED);
        TermModel saved = termRepository.save(terms);
        log.info("Nova versão de termo publicada: {} v{}", dto.type(), dto.version());
        return saved;
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
        userTermAcceptanceRepository.deleteAll(user.getId());
        user.setActive(false);
        userRepository.save(user);
    }
}
