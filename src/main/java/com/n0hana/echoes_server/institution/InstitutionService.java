package com.n0hana.echoes_server.institution;

import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

/**
 * Service para o manipulação de instituições.
 * 
 * @since 0.1.0
 * @author Miguel Santana da Costa
 * @see {@link InstituicionalModel}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final InstitutionRepository repository;

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
    public InstitutionModel create(InstitutionModel model) {
        String cleanCnpj = model.getCnpj().replaceAll("\\D", "");

        if (repository.existsByCnpjOrEmail(cleanCnpj, model.getEmail())) {
            throw new DuplicateKeyException("Instituição já cadastrada com este CNPJ ou E-mail.");
        }
        model.setCnpj(cleanCnpj);

        return repository.save(model);
    }

    /***
     * Busca e filtra instituições cadastradas no sistema.
     * 
     * <p>
     * O método realiza a consulta de todas as instituições cadastradas, ou filtra
     * as instituições pelo seu nome.
     * </p>
     * 
     * @param name Nome da instituição a ser filtrada.
     * @param size Quantidade de elementos por paginação.
     * @param page Número da página acessado na paginação.
     * @return {@link Page} Contêm às instituições que atendem aos parâmetros.
     */
    @Transactional(readOnly = true)
    public List<InstitutionModel> findAll(String name, int size, int pageNumber, String sort) {
        Page<InstitutionModel> page;

        Sort sortSpec = this.parseSort(sort);
        Pageable pageable = PageRequest.of(pageNumber, size, sortSpec);

        if (name != null && !name.isBlank())
            page = repository.findByNameContainingIgnoreCase(name, pageable);
        else
            page = repository.findAll(pageable);

        return page.toList();
    }

    /**
     * Busca uma instituição por id.
     * 
     * @param id Id da instituição a ser filtrada.
     * @return {@link InstituitionModel} encontrada pela busca.
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
     * @param id Id da instuição a ser alterada.
     */
    @Transactional
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
