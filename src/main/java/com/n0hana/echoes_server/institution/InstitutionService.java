package com.n0hana.echoes_server.institution;

import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * {@link IllegalArgumentException}.
     * Do contrário, os dados da instituição são armazenados no banco de dados.
     * </p>
     * 
     * @throws IllegalArgumentException
     * 
     * @param dto Objeto com dados para cadastro de uma instituição.
     * @return {@link InstitutionModel} cadastrado no banco de dados.
     */
    @Transactional
    public InstitutionDTO create(InstitutionDTO dto) {
        String cleanCnpj = dto.cnpj().replaceAll("\\D", "");

        if (repository.existsByCnpjOrEmail(cleanCnpj, dto.email())) {
            throw new IllegalArgumentException("Instituição já cadastrada com este CNPJ ou E-mail.");
        }

        InstitutionModel model = InstitutionModel.builder()
                .name(dto.name())
                .acronym(dto.acronym())
                .cnpj(cleanCnpj)
                .email(dto.email())
                .phone(dto.phone())
                .address(dto.address())
                .build();

        return toDTO(repository.save(model));
    }

    /***
     * Busca e filtra instituições cadastradas no sistema.
     * 
     * <p>
     * O método realiza a consulta de todas as instituições cadastradas, ou filtra
     * as instituições pelo seu nome.
     * </p>
     * 
     * @param name     Nome da instituição a ser filtrada.
     * @param pageable Configuração para paginação dos resultados.
     * @return {@link Page} Contêm às instituições que atendem aos parâmetros.
     */
    @Transactional(readOnly = true)
    public Page<InstitutionDTO> findAll(String name, Pageable pageable) {
        Page<InstitutionModel> page = (name != null && !name.isBlank())
                ? repository.findByNameContainingIgnoreCase(name, pageable)
                : repository.findAll(pageable);
        return page.map(this::toDTO);
    }

    /**
     * Busca uma instituição por id.
     * 
     * @param id Id da instituição a ser filtrada.
     * @return {@link InstituitionModel} encontrada pela busca.
     */
    @Transactional(readOnly = true)
    public InstitutionDTO findById(UUID id) {
        return toDTO(getInstitutionOrThrow(id));
    }

    /**
     * Atualiza os dados de uma instituição.
     * 
     * @param id  Id da instituição a ser atualizada.
     * @param dto Objeto com dados a serem atualizados.
     * @return {@link InstitutionModel} atualizado com novos dados,
     */
    @Transactional
    public InstitutionDTO update(UUID id, InstitutionDTO dto) {
        InstitutionModel model = getInstitutionOrThrow(id);

        model.setName(dto.name());
        model.setAcronym(dto.acronym());
        model.setPhone(dto.phone());
        model.setAddress(dto.address());

        return toDTO(model);
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
     * Transforma um objeto da classe {@link InstituitionModel} em um objeto
     * {@link InstitutionDTO} para respostas de requisições HTTP.
     * 
     * @param model
     * @return
     */
    private InstitutionDTO toDTO(InstitutionModel model) {
        return new InstitutionDTO(
                model.getId(),
                model.getName(),
                model.getAcronym(),
                model.getCnpj(),
                model.getEmail(),
                model.getPhone(),
                model.getAddress(),
                model.isActive());
    }
}
