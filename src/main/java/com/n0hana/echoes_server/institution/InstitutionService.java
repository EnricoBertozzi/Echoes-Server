package com.n0hana.echoes_server.institution;

import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final InstitutionRepository repository;

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
                .build();

        return toDTO(repository.save(model));
    }

    @Transactional(readOnly = true)
    public Page<InstitutionDTO> findAll(String name, Pageable pageable) {
        Page<InstitutionModel> page = (name != null && !name.isBlank())
                ? repository.findByNameContainingIgnoreCase(name, pageable)
                : repository.findAll(pageable);
        return page.map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public InstitutionDTO findById(UUID id) {
        return toDTO(getInstitutionOrThrow(id));
    }

    @Transactional
    public InstitutionDTO update(UUID id, InstitutionDTO dto) {
        InstitutionModel model = getInstitutionOrThrow(id);

        model.setName(dto.name());
        model.setAcronym(dto.acronym());
        model.setPhone(dto.phone());

        return toDTO(model);
    }

    @Transactional
    public void toggleStatus(UUID id) {
        InstitutionModel model = getInstitutionOrThrow(id);
        model.setActive(!model.isActive());
    }

    @Transactional
    public void delete(UUID id) {
        InstitutionModel model = getInstitutionOrThrow(id);
        model.setDeleted(true);
    }

    private InstitutionModel getInstitutionOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new InstitutionNotFoundException("Instituição não encontrada com ID: " + id));
    }

    private InstitutionDTO toDTO(InstitutionModel model) {
        return new InstitutionDTO(
                model.getId(),
                model.getName(),
                model.getAcronym(),
                model.getCnpj(),
                model.getEmail(),
                model.getPhone(),
                model.isActive()
        );
    }
}
