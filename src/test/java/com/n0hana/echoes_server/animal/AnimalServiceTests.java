package com.n0hana.echoes_server.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;

import com.n0hana.echoes_server.animal.AnimalModel;

@ExtendWith(MockitoExtension.class)
public class AnimalServiceTests {

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private AnimalPublicKeyService publicKeyService;

    @InjectMocks
    private AnimalService animalService;

    @Test
    @DisplayName("Cadastro de novo animal com pontos de ausculta e cenários")
    public void saveNewAnimalInDatabase() {
        // Arrange

        AnimalModel animal = AnimalModel.builder()
                .name("Labrador")
                .description("Cão labrador para ausculta pulmonar e cardiaca")
                .model("Tches")
                .mac("AA:BB:CC:DD:EE:FF")
                .build();

        MockMultipartFile publicKeyFile = new MockMultipartFile(
                "publicKey",
                "public-key.pem",
                "text/plain",
                "-----BEGIN PUBLIC KEY-----conteudo-----END PUBLIC KEY-----".getBytes());

        when(publicKeyService.readFrom(publicKeyFile)).thenReturn("-----BEGIN PUBLIC KEY-----conteudo-----END PUBLIC KEY-----");
        when(animalRepository.findByMac(animal.getMac())).thenReturn(Optional.empty());
        when(animalRepository.save(animal)).thenReturn(animal);

        // Act
        AnimalModel savedAnimal = animalService.save(animal, publicKeyFile);

        // Assert
        assertEquals(animal, savedAnimal);
        assertEquals("-----BEGIN PUBLIC KEY-----conteudo-----END PUBLIC KEY-----", savedAnimal.getPublicKey());
    }

    @Test
    @DisplayName("Busca animal pelo MAC informado no token do dispositivo")
    public void shouldFindAnimalByMac() {
        // Arrange
        AnimalModel animal = AnimalModel.builder()
                .name("Labrador")
                .description("Cão labrador para ausculta pulmonar e cardiaca")
                .model("Tches")
                .mac("AA:BB:CC:DD:EE:FF")
                .build();

        when(animalRepository.findByMac(animal.getMac())).thenReturn(Optional.of(animal));

        // Act
        AnimalModel foundAnimal = animalService.findAnimalByMac(animal.getMac());

        // Assert
        assertEquals(animal, foundAnimal);
    }

    @Test
    @DisplayName("Verifica a quantidade de elementos retornadas em um page")
    public void verifyFindAnimalsByNameWithPageSize() {
        // Arrange
        AnimalModel animal1 = AnimalModel.builder()
                .name("Cachorro Juvenil")
                .description("Modelo Canino Juvenil")
                .model("Snoop")
                .build();

        AnimalModel animal2 = AnimalModel.builder()
                .name("Cachorro Adulto")
                .description("Modelo Canino Adulto")
                .model("Tches")
                .build();

        List<AnimalModel> expectedAnimals = List.of(animal1, animal2);

        int size = 2;
        int page = 0;
        String name = "Cachorro";

        Page<AnimalModel> mockPage = new PageImpl<>(
                expectedAnimals,
                PageRequest.of(page, size),
                3);

        when(animalRepository.findByNameContaining(
                name,
                PageRequest.of(
                        page,
                        size,
                        Sort.by("name"))))
                .thenReturn(mockPage);

        // Act
        List<AnimalModel> list = animalService.findAnimalsByName(name, page, size);

        // Assert
        assertNotNull(list);

        // Verifica se a pagina retornada possui os dois elementos
        assertEquals(2, list.size());

        // Verifica se todos os elementos foram retornados
        assertEquals(expectedAnimals, list);
    }

    @Test
    @DisplayName("Verifica retorno vazio de busca além da quantidade de elementos")
    public void verifyFindAnimalsByNameWithPageOutOfBounds() {
        // Arrange
        int page = 5;
        int size = 2;
        String name = "Cachorro";

        Page<AnimalModel> mockPage = new PageImpl<>(
                List.of(),
                PageRequest.of(page, size),
                3);

        when(animalRepository.findByNameContaining(
                name,
                PageRequest.of(
                        page,
                        size,
                        Sort.by("name"))))
                .thenReturn(mockPage);

        // Act
        List<AnimalModel> list = animalService.findAnimalsByName(name, page, size);

        // Assert
        assertNotNull(list);

        // Verifica se não há elementos na lista
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("Vericica a alteração de dados de um animal")
    public void verifyForChangesInAnimal() {
        // Arrage
        UUID id = UUID.fromString("1111-2222-3333-4444-555555555555");
        
        AnimalModel animal = AnimalModel.builder()
        .id(id)
        .name("Cachorro Juvenil")
        .description("Modelo de Canino Juvenil")
        .model("Tches")
        .build();
    }
}
