package com.n0hana.echoes_server.classroom.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.n0hana.echoes_server.user.model.Teacher;

class ClassroomMaterialControllerCoverageTests {

    private final ClassroomMaterialService service = mock(ClassroomMaterialService.class);
    private final ClassroomMaterialController controller = new ClassroomMaterialController(service);
    private final Principal principal = () -> "prof@exemplo.test";
    private final UUID classroomId = UUID.randomUUID();
    private final UUID materialId = UUID.randomUUID();
    private final MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] { 1 });
    private ClassroomMaterial material;

    @BeforeEach
    void setUp() {
        Teacher author = new Teacher();
        author.setId(UUID.randomUUID());
        author.setName("Maria");
        author.setActive(true);
        material = new ClassroomMaterial();
        material.setId(materialId);
        material.setAuthor(author);
        material.setTitle("Aula");
        material.setOriginalFileName("a.pdf");
        material.setContentType("application/pdf");
    }

    @Test
    @DisplayName("Lista delega ao serviço com o e-mail do principal")
    void listDelegates() {
        List<ClassroomMaterialDTO.Info> infos = List.of();
        when(service.list("prof@exemplo.test", classroomId)).thenReturn(infos);

        assertSame(infos, controller.list(classroomId, principal));
    }

    @Test
    @DisplayName("Criar responde 201 com o DTO")
    void createReturns201() {
        when(service.create("prof@exemplo.test", classroomId, "Aula", "d", file)).thenReturn(material);

        ResponseEntity<ClassroomMaterialDTO.Info> response = controller.create(classroomId, "Aula", "d", file,
                principal);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Aula", response.getBody().title());
    }

    @Test
    @DisplayName("Atualizar e substituir devolvem o DTO do material alterado")
    void updateAndReplace() {
        var request = new ClassroomMaterialDTO.Update("Aula", "d");
        when(service.update("prof@exemplo.test", classroomId, materialId, request)).thenReturn(material);
        when(service.replace("prof@exemplo.test", classroomId, materialId, file)).thenReturn(material);

        assertEquals(materialId, controller.update(classroomId, materialId, request, principal).id());
        assertEquals(materialId, controller.replace(classroomId, materialId, file, principal).id());
    }

    @Test
    @DisplayName("Excluir responde 204 sem corpo")
    void deleteReturns204() {
        ResponseEntity<Void> response = controller.delete(classroomId, materialId, principal);

        verify(service).delete("prof@exemplo.test", classroomId, materialId);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
    }

    private ResponseEntity<Resource> download(boolean inline, boolean previewable, String name) {
        Resource resource = new ByteArrayResource(new byte[] { 1 });
        when(service.download("prof@exemplo.test", classroomId, materialId)).thenReturn(
                new ClassroomMaterialService.Download(resource, name, "application/pdf", previewable,
                        UUID.randomUUID()));
        return controller.download(classroomId, materialId, inline, principal);
    }

    @Test
    @DisplayName("Download inline e previewable usa Content-Disposition inline")
    void downloadInline() {
        ResponseEntity<Resource> response = download(true, true, "a.pdf");

        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertEquals(true, disposition.startsWith("inline;") && disposition.contains("a.pdf"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    @DisplayName("Inline em tipo não previewable ou sem inline vira attachment")
    void downloadAttachment() {
        assertEquals(true, download(true, false, "a.pdf").getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .startsWith("attachment;"));
        assertEquals(true, download(false, true, "a.pdf").getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .startsWith("attachment;"));
    }

    @Test
    @DisplayName("Nome com acento é codificado em UTF-8")
    void downloadUtf8Name() {
        String header = download(false, true, "ação.pdf").getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertEquals(true, header.contains("filename*=UTF-8''"));
    }

    @Test
    @DisplayName("Handler converte MaterialException e upload grande demais")
    void exceptionHandler() {
        MaterialExceptionHandler handler = new MaterialExceptionHandler();

        ResponseEntity<Map<String, String>> notFound = handler.handle(MaterialException.notFound());
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("Material não encontrado", notFound.getBody().get("message"));

        ResponseEntity<Map<String, String>> invalid = handler.handle(MaterialException.invalid("ruim"));
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        assertEquals("ruim", invalid.getBody().get("message"));

        ResponseEntity<Map<String, String>> large = handler.tooLarge(new MaxUploadSizeExceededException(1L));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, large.getStatusCode());
        assertEquals("Arquivo maior que o limite de upload do servidor", large.getBody().get("message"));
    }

    @Test
    @DisplayName("MaterialException expõe status e mensagem")
    void materialException() {
        MaterialException ex = new MaterialException(HttpStatus.CONFLICT, "x");
        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertEquals("x", ex.getMessage());
    }
}
