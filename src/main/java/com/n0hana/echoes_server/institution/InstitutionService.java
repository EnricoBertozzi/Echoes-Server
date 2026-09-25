package com.n0hana.echoes_server.institution;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.cnpj.CnpjDTO;
import com.n0hana.echoes_server.cnpj.CnpjService;
import com.n0hana.echoes_server.cnpj.CnpjValidator;
import com.n0hana.echoes_server.cnpj.exception.CnpjProviderIndisponivelException;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;
import com.n0hana.echoes_server.institution.exception.InstitutionPendingVerificationException;
import com.n0hana.echoes_server.notifier.InstitutionNotificationData;
import com.n0hana.echoes_server.notifier.InstitutionNotifier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service para a manipulação de instituições.
 * 
 * @since 0.1.1
 * @author Miguel Santana da Costa
 * @see {@link InstitutionModel}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final InstitutionRepository repository;
    private final CnpjService cnpjService;
    private final InstitutionVerificationService verificationService;
    private final InstitutionNotifier notifier;

    /**
     * Salva uma nova instituição no banco de dados.
     * 
     * <p>
     * O método valida o cnpj enviado para cadastro verificando se não existe
     * instituição com ele cadastrado, caso existe envia uma exceção
     * {@link DuplicateKeyException}.
     * Do contrário, os dados da instituição são armazenados no banco de dados.
     * </p>
     * 
     * @throws DuplicateKeyException
     * 
     * @param model Instituição a ser cadastrada no sistema.
     * @return {@link InstitutionModel} cadastrado no banco de dados.
     */
    @Transactional
    @Auditable(action = "CREATE", entity = "Institution")
    public InstitutionModel create(InstitutionModel model) {
        String cleanCnpj = CnpjValidator.normalizar(model.getCnpj());
        model.setCnpj(cleanCnpj);

        if (repository.existsByCnpjOrEmail(cleanCnpj, model.getEmail())) {
            throw new DuplicateKeyException("Instituição já cadastrada com este CNPJ ou E-mail.");
        }

        boolean pendingNotification = false;

        try {
            CnpjDTO dados = cnpjService.consultar(cleanCnpj);
            verificationService.applyDadosReceita(model, dados);
            model.setVerificationStatus(InstitutionVerificationStatus.VERIFIED);
        } catch (CnpjProviderIndisponivelException e) {
            // Brasil API caiu: permite cadastro com pendência, scheduler reverifica depois.
            log.warn("Brasil API indisponível ao cadastrar instituição CNPJ {}. Marcada como PENDING_VERIFICATION.",
                    cleanCnpj);
            model.setVerificationStatus(InstitutionVerificationStatus.PENDING_VERIFICATION);
            pendingNotification = true;
        }
        // CnpjInvalidoException (400) e CnpjNaoEncontradoException (404) propagam — não
        // persiste

        InstitutionModel saved = repository.save(model);

        if (pendingNotification) {
            notifier.notifyPendingVerification(
                    InstitutionNotificationData.from(saved),
                    "Brasil API indisponível no momento do cadastro");
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public void assertCanPurchase(UUID institutionId) {
        InstitutionModel inst = getInstitutionOrThrow(institutionId);
        if (inst.getVerificationStatus() != InstitutionVerificationStatus.VERIFIED) {
            throw new InstitutionPendingVerificationException(institutionId);
        }
    }

    public int retryPendingVerifications() {
        List<UUID> ids = repository.findAllByVerificationStatus(
                InstitutionVerificationStatus.PENDING_VERIFICATION)
                .stream().map(InstitutionModel::getId).toList();

        int success = 0;
        for (UUID id : ids) {
            try {
                if (verificationService.tryVerify(id) == InstitutionVerificationService.VerificationOutcome.VERIFIED) {
                    success++;
                }
            } catch (Exception e) {
                log.error("Erro ao reverificar instituição {}", id, e);
            }
        }
        return success;
    }

    /***
     * Busca e filtra instituições cadastradas no sistema.
     * 
     * <p>
     * O método realiza a consulta de todas as instituições cadastradas, ou filtra
     * as instituições pelo seu nome.
     * </p>
     * 
     * @param name       Nome da instituição a ser filtrada.
     * @param size       Quantidade de elementos por paginação.
     * @param pageNumber Número da página acessado na paginação.
     * @param sort       Parâmetro de ordenação.
     * @return {@link Page} Contêm as instituições que atendem aos parâmetros.
     */
    @Transactional(readOnly = true)
    public Page<InstitutionModel> findAll(String name, int size, int pageNumber, String sort) {
        Sort sortSpec = this.parseSort(sort);
        Pageable pageable = PageRequest.of(pageNumber, size, sortSpec);

        if (name != null && !name.isBlank()) {
            return repository.findByNameContainingIgnoreCase(name, pageable);
        }
        return repository.findAll(pageable);
    }

    /**
     * Busca uma instituição por id.
     * 
     * @param id Id da instituição a ser filtrada.
     * @return {@link InstitutionModel} encontrada pela busca.
     */
    @Transactional(readOnly = true)
    public InstitutionModel findById(UUID id) {
        return getInstitutionOrThrow(id);
    }

    /**
     * Atualiza os dados de uma instituição.
     * 
     * @param id    Id da instituição a ser atualizada.
     * @param model Instituição com dados a serem atualizados.
     * @return {@link InstitutionModel} atualizado com novos dados,
     */
    @Transactional
    @Auditable(action = "UPDATE", entity = "Institution")
    public InstitutionModel update(UUID id, InstitutionModel model) {
        InstitutionModel savedModel = getInstitutionOrThrow(id);

        if (!savedModel.getName().equals(model.getName()))
            savedModel.setName(model.getName());

        if (!savedModel.getAcronym().equals(model.getAcronym()))
            savedModel.setAcronym((model.getAcronym()));

        if (!savedModel.getPhone().equals(model.getPhone()))
            savedModel.setPhone((model.getPhone()));

        if (!savedModel.getAddress().equals(model.getAddress()))
            savedModel.setAddress((model.getAddress()));

        return repository.save(savedModel);
    }

    /**
     * Altera o estado ativo de uma instituição.
     * 
     * @param id Id da instituição a ser alterada.
     */
    @Transactional
    @Auditable(action = "TOGGLE_STATUS", entity = "Institution")
    public void toggleStatus(UUID id) {
        InstitutionModel model = getInstitutionOrThrow(id);
        model.setActive(!model.isActive());

        repository.save(model);
    }

    /**
     * Desativa uma instituição do sistema.
     * 
     * @param id Id da instituição a ser desativada.
     */

    @Transactional
    @Auditable(action = "DELETE", entity = "Institution")
    public void delete(UUID id) {
        InstitutionModel model = getInstitutionOrThrow(id);
        model.setDeleted(true);

        repository.save(model);
    }

    /**
     * Verifica se uma instituição está cadastrada.
     * 
     * @param id Id da instituição a verificada.
     * 
     * @throws InstitutionNotFoundException
     * 
     * @return {@link InstitutionModel} encontrada na busca.
     */
    private InstitutionModel getInstitutionOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new InstitutionNotFoundException("Instituição não encontrada com ID: " + id));
    }

    /**
     * Método auxiliar para criar ordenação.
     *
     * @param sort String com os parâmetros de ordenação.
     * 
     * @return {@link Sort} Objeto de ordenação configurado.
     */
    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && parts[1].equalsIgnoreCase("desc"))
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }
}
