package com.n0hana.echoes_server.term;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.term.dto.CreateTermRequestDTO;
import com.n0hana.echoes_server.term.exception.TermNotFoundException;
import com.n0hana.echoes_server.term.exception.TermStatusException;
import com.n0hana.echoes_server.term.exception.TermVersionException;
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

    private static final List<DocumentType> SUPPORTED_DOCUMENT_TYPES = List.of(
        DocumentType.TERMS_OF_USE,
        DocumentType.PRIVACY_POLICY,
        DocumentType.COOKIES_POLICY,
        DocumentType.PROCESSING_OPERATIONS
    );

    private final TermRepository termRepository;
    private final UserTermAcceptanceRepository userTermAcceptanceRepository;
    private final UserRepository userRepository;

    // ------------------------------------------------------------------ consultas

    public TermModel getActiveTerms(DocumentType type) {
        return termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(type, TermStatus.PUBLISHED)
            .orElseThrow(() -> new TermNotFoundException("Nenhum termo ativo do tipo " + type));
    }

    public TermModel getLatestTerms(DocumentType type) {
        return termRepository.findTopByTypeOrderByTimestampDesc(type)
            .orElseThrow(() -> new TermNotFoundException("Nenhum termo do tipo " + type));
    }

    public boolean hasAcceptedLatestTerms(UUID userId, DocumentType type) {
        Optional<TermModel> active = termRepository
            .findFirstByTypeAndStatusOrderByTimestampDesc(type, TermStatus.PUBLISHED);
        if (active.isEmpty()) {
            // Sem termo publicado não há o que aceitar.
            return true;
        }
        return userTermAcceptanceRepository
            .findByUserIdAndTermsId(userId, active.get().getId())
            .isPresent();
    }

    /**
     * Tipos obrigatórios cujo termo PUBLISHED atual ainda não foi aceito pelo usuário.
     * Usado para exigir novo aceite quando o ADMIN publica uma versão nova.
     */
    public List<DocumentType> getPendingRequiredTerms(UUID userId) {
        List<DocumentType> pending = termRepository.findTypesWithoutAcceptance(
            userId, REQUIRED_DOCUMENTS, TermStatus.PUBLISHED);
        return REQUIRED_DOCUMENTS.stream()
            .filter(pending::contains)
            .toList();
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
    }

    @Auditable(action = "ACCEPT_TERM", entity = "Term")
    public UserTermAcceptance acceptTerms(UUID userId, DocumentType type) {
        User user = userRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);
        return acceptTermsInternal(user, type);
    }

    private UserTermAcceptance acceptTermsInternal(User user, DocumentType type) {
        TermModel terms = getActiveTerms(type);
        return userTermAcceptanceRepository
            .findByUserIdAndTermsId(user.getId(), terms.getId())
            .orElseGet(() -> {
                UserTermAcceptance saved = userTermAcceptanceRepository
                    .save(new UserTermAcceptance(user, terms));
                log.debug("Aceite registrado: user={}, termId={}, version={}",
                    user.getId(), terms.getId(), terms.getVersion());
                return saved;
            });
    }

    public List<UserTermAcceptance> getUserAcceptances(UUID userId) {
        return userTermAcceptanceRepository.findAll().stream()
            .filter(a -> a.getUser().getId().equals(userId))
            .toList();
    }

    // ------------------------------------------------------------------ admin

    /**
     * Lista todos os termos para a administração, do mais recente para o mais antigo.
     * Os filtros são opcionais: sem eles, retorna o histórico completo (incluindo
     * rascunhos e versões arquivadas).
     */
    public List<TermModel> getAllTerms(DocumentType type, TermStatus status) {
        List<TermModel> terms = termRepository.findAllByTypes(
            type == null ? SUPPORTED_DOCUMENT_TYPES : List.of(type));

        return terms.stream()
            .filter(term -> status == null || term.getStatus() == status)
            .toList();
    }

    public TermModel getTermById(Long id) {
        return termRepository.findById(id)
            .orElseThrow(TermNotFoundException::new);
    }

    @Transactional
    public TermModel createTerm(CreateTermRequestDTO dto) {
        TermStatus targetStatus = dto.status() == null ? TermStatus.DRAFT : dto.status();

        // Valida formato da versão
        parseVersion(dto.version());

        TermModel term = new TermModel();
        term.setVersion(dto.version());
        term.setContent(dto.content());
        term.setType(dto.type());

        if (targetStatus == TermStatus.PUBLISHED) {
            termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(dto.type(), TermStatus.PUBLISHED)
                .ifPresent(active -> {
                    if (!isVersionGreater(dto.version(), active.getVersion())) {
                        throw new TermVersionException(
                            "Versão deve ser maior que a atual (" + active.getVersion() + ")");
                    }
                    active.setStatus(TermStatus.ARCHIVED);
                    termRepository.save(active);
                });
            term.setStatus(TermStatus.PUBLISHED);
            TermModel saved = termRepository.save(term);
            return saved;
        }

        term.setStatus(targetStatus);
        TermModel saved = termRepository.save(term);
        return saved;
    }

    @Transactional
    public TermModel updateTermContent(Long id, String content) {
        if (content == null || content.isBlank()) {
            throw new TermStatusException("Conteúdo é obrigatório");
        }

        TermModel term = getTermById(id);

        if (term.getStatus() != TermStatus.DRAFT) {
            throw new TermStatusException(
                "Somente termos com status DRAFT podem ter o conteúdo editado");
        }

        term.setContent(content);
        TermModel saved = termRepository.save(term);
        return saved;
    }

    @Transactional
    public TermModel updateTermStatus(Long id, TermStatus newStatus) {
        if (newStatus == null) {
            throw new TermStatusException("Status é obrigatório");
        }

        TermModel term = getTermById(id);

        if (term.getStatus() == newStatus) {
            return term;
        }

        if (newStatus == TermStatus.PUBLISHED) {
            termRepository.findFirstByTypeAndStatusOrderByTimestampDesc(term.getType(), TermStatus.PUBLISHED)
                .filter(active -> active.getId() != term.getId())
                .ifPresent(active -> {
                    active.setStatus(TermStatus.ARCHIVED);
                    termRepository.save(active);
                });
            term.setStatus(TermStatus.PUBLISHED);
            TermModel saved = termRepository.save(term);
            return saved;
        }

        if (term.getStatus() == TermStatus.PUBLISHED) {
            throw new TermStatusException(
                "Não é possível alterar o status do termo atualmente publicado. " +
                "Publique outro termo do mesmo tipo para manter sempre um termo publicado.");
        }

        term.setStatus(newStatus);
        TermModel saved = termRepository.save(term);
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
                throw new TermVersionException(
                    "Formato de versão inválido. Use major.minor.patch (ex: 2.0.0)");
            }
            return parts;
        } catch (NumberFormatException e) {
            throw new TermVersionException(
                "Formato de versão inválido. Use major.minor.patch (ex: 2.0.0)");
        }
    }

    @Transactional
    public void revoke(User user) {
        userTermAcceptanceRepository.deleteAll(user.getId());
        user.setActive(false);
        userRepository.save(user);
    }
}
