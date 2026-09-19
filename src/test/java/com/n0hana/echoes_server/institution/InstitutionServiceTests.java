package com.n0hana.echoes_server.institution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;

@ExtendWith(MockitoExtension.class)
public class InstitutionServiceTests {

  @Mock
  private InstitutionRepository repository;

  @InjectMocks
  private InstitutionService service;

  private InstitutionModel createTestModel(UUID id) {
    InstitutionModel model = InstitutionModel.builder()
        .id(id)
        .name("Instituição Teste")
        .acronym("IT")
        .cnpj("12.345.678/0001-90")
        .email("contato@teste.com")
        .phone("11999999999")
        .address("Rua Teste, 123")
        .active(true)
        .deleted(false)
        .build();
    return model;
  }

  @Test
  @DisplayName("Deve criar uma instituição com sucesso")
  void shouldCreateSuccessfulInstitution() {
    // Arrange
    InstitutionModel model = this.createTestModel(null);

    String expectedCnpj = "12345678000190";

    when(repository.existsByCnpjOrEmail(expectedCnpj, model.getEmail()))
        .thenReturn(false);
    when(repository.save(model)).thenReturn(model);

    // Act
    InstitutionModel result = service.create(model);

    // Assert
    assertNotNull(result);
    assertEquals(expectedCnpj, result.getCnpj());
    verify(repository, times(1)).existsByCnpjOrEmail(expectedCnpj, model.getEmail());
  }

  @Test
  @DisplayName("Deve levantar exceção por usuário já cadastrado")
  void shouldThrowDuplicationKeyException() {
    // Arrange
    InstitutionModel model = this.createTestModel(null);

    String expectedCnpj = "12345678000190";

    // Act
    when(repository.existsByCnpjOrEmail(expectedCnpj, model.getEmail()))
        .thenReturn(true);

    // Assert
    assertThrows(DuplicateKeyException.class, () -> {
      service.create(model);
    });

    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("Deve filtrar as instituições cadastradas por nome quando o parâmetro é fornecido")
  void shouldFilterInstitutionsByNameWhenParameterIsProvided() {
    // Arrange
    InstitutionModel model = this.createTestModel(null);

    String searchName = "Teste";
    PageImpl<InstitutionModel> page = new PageImpl<>(List.of(
        model));

    when(repository.findByNameContainingIgnoreCase(eq(searchName), any(Pageable.class))).thenReturn(page);

    // Act
    List<InstitutionModel> result = service.findAll(searchName, 10, 0, "name,asc");

    // Assert
    assertNotNull(result);
    assertEquals(result.size(), 1);
    verify(repository).findByNameContainingIgnoreCase(eq(searchName), any(Pageable.class));
    verify(repository, never()).findAll(any(Pageable.class));
  }

  @Test
  @DisplayName("Deve buscar todas as instituições cadastradas")
  void shouldFindAllInstituitions() {
    // Arrange
    InstitutionModel model = this.createTestModel(null);

    PageImpl<InstitutionModel> page = new PageImpl<>(List.of(model));

    when(repository.findAll(any(Pageable.class))).thenReturn(page);

    // Act
    List<InstitutionModel> result = service.findAll(null, 10, 0, "name,asc");

    // Assert
    assertNotNull(result);
    assertEquals(result.size(), 1);
    verify(repository).findAll(any(Pageable.class));
    verify(repository, never()).findByNameContainingIgnoreCase(eq(null), any(Pageable.class));
  }

  @Test
  @DisplayName("Deve buscar uma instituição cadastrada no sistema pelo id")
  void shouldFindInstitutionById() {
    // Arrange
    UUID uuid = UUID.fromString("be230414-d7ab-4448-b100-f29bb50609bb");

    InstitutionModel model = this.createTestModel(uuid);

    when(repository.findById(uuid)).thenReturn(Optional.of(model));

    // Act
    InstitutionModel result = service.findById(uuid);

    // Assert
    assertNotNull(result);
    assertEquals(model.getId(), result.getId());
    verify(repository).findById(uuid);
  }

  @Test
  @DisplayName("Deve lançar execeção de instituição não encontrada quando id não está cadastrado")
  void shouldThrowInstitutionNotFoundExceptionWhenIdNotFound() {
    // Arrange
    UUID uuid = UUID.fromString("be230414-d7ab-4448-b100-f29bb50609bb");

    when(repository.findById(uuid)).thenReturn(Optional.empty());

    // Act
    assertThrows(InstitutionNotFoundException.class, () -> {
      service.findById(uuid);
    });

    // Assert
    verify(repository).findById(uuid);
  }

  @Test
  @DisplayName("Deve alterar os campos da instituição com novos campos")
  void shouldUpdateInstitutionWithNewValues() {
    // Arrange
    UUID uuid = UUID.randomUUID();
    InstitutionModel model = this.createTestModel(uuid);

    InstitutionModel updatedModel = InstitutionModel.builder()
        .name("Universidade Novo Mundo")
        .acronym("UNM")
        .phone("1101234567")
        .address("Rua de baixo, 123")
        .build();

    when(repository.findById(uuid)).thenReturn(Optional.of(model));
    when(repository.save(any(InstitutionModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    InstitutionModel result = service.update(uuid, updatedModel);

    // Assert
    assertNotNull(result);
    assertEquals(updatedModel.getName(), result.getName());
    assertEquals(updatedModel.getAcronym(), result.getAcronym());
    assertEquals(updatedModel.getPhone(), result.getPhone());
    assertEquals(updatedModel.getAddress(), result.getAddress());

    verify(repository).save(model);
  }

  @Test
  @DisplayName("Deve alterar o estado da instituição para desativo")
  void shouldToggleInstitutionStatusToDisable() {
    // Arrange
    UUID uuid = UUID.randomUUID();
    InstitutionModel model = spy(createTestModel(uuid));
    model.setActive(true);

    when(repository.findById(uuid)).thenReturn(Optional.of(model));
    when(repository.save(model)).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    service.toggleStatus(uuid);

    // Assert
    verify(model).setActive(eq(false));
  }

  @Test
  @DisplayName("Deve alterar o estado da instituição para ativo")
  void shouldToggleInstitutionStatusToActive() {
    // Arrange
    UUID uuid = UUID.randomUUID();
    InstitutionModel model = spy(createTestModel(uuid));
    model.setActive(false);

    when(repository.findById(uuid)).thenReturn(Optional.of(model));
    when(repository.save(model)).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    service.toggleStatus(uuid);

    // Assert
    verify(model).setActive(eq(true));
  }

  @Test
  @DisplayName("Deve alterar o estado de remoção da instituição para ativo")
  void shouldToggleInstitutionDeleteStatusToTrue() {
    // Arrange
    UUID uuid = UUID.randomUUID();
    InstitutionModel model = spy(createTestModel(uuid));
    model.setDeleted(false);

    when(repository.findById(uuid)).thenReturn(Optional.of(model));
    when(repository.save(model)).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    service.delete(uuid);

    // Assert
    verify(model).setDeleted(eq(true));
  }
}
