package com.n0hana.echoes_server.institution;

import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    public Page<InstitutionDTO> findAll(String name, Pageable pageable) {
        Page<InstitutionModel> page = (name != null && !name.isBlank())
                ? repository.findByNameContainingIgnoreCase(name, pageable)
                : repository.findAll(pageable);
        return page.map(this::toDTO);
    }

    public InstitutionDTO findById(UUID id) {
        return repository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new InstitutionNotFoundException("Instituição não encontrada com ID: " + id));
    }

    @Transactional
    public InstitutionDTO update(UUID id, InstitutionDTO dto) {
        InstitutionModel model = repository.findById(id)
                .orElseThrow(() -> new InstitutionNotFoundException("Instituição não encontrada com ID: " + id));

        model.setName(dto.name());
        model.setAcronym(dto.acronym());
        model.setPhone(dto.phone());

        return toDTO(repository.save(model));
    }

    @Transactional
    public void toggleStatus(UUID id) {
        InstitutionModel model = repository.findById(id)
                .orElseThrow(() -> new InstitutionNotFoundException("Instituição não encontrada com ID: " + id));
        model.setActive(!model.isActive());
        repository.save(model);
    }

    @Transactional
    public void delete(UUID id) {
        InstitutionModel model = repository.findById(id)
                .orElseThrow(() -> new InstitutionNotFoundException("Instituição não encontrada com ID: " + id));
        model.setDeleted(true);
        repository.save(model);
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
