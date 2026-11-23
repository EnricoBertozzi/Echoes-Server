package com.n0hana.echoes_server.classroom.material;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import com.n0hana.echoes_server.infra.security.SecurityConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/classrooms/{classroomId}/materials")
@SecurityRequirement(name = SecurityConfig.SECURITY)
@RequiredArgsConstructor
public class ClassroomMaterialController {
    private final ClassroomMaterialService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEACHER', 'STUDENT')")
    public List<ClassroomMaterialDTO.Info> list(@PathVariable UUID classroomId, Principal principal) {
        return service.list(principal.getName(), classroomId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassroomMaterialDTO.Info> create(@PathVariable UUID classroomId,
            @RequestParam String title, @RequestParam(required = false) String description,
            @RequestPart("file") MultipartFile file, Principal principal) {
        ClassroomMaterial created = service.create(principal.getName(), classroomId, title, description, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClassroomMaterialDTO.Info.from(created));
    }

    @PatchMapping("/{materialId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ClassroomMaterialDTO.Info update(@PathVariable UUID classroomId, @PathVariable UUID materialId,
            @RequestBody @Valid ClassroomMaterialDTO.Update request, Principal principal) {
        return ClassroomMaterialDTO.Info.from(service.update(principal.getName(), classroomId, materialId, request));
    }

    @PatchMapping(value = "/{materialId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public ClassroomMaterialDTO.Info replace(@PathVariable UUID classroomId, @PathVariable UUID materialId,
            @RequestPart("file") MultipartFile file, Principal principal) {
        return ClassroomMaterialDTO.Info.from(service.replace(principal.getName(), classroomId, materialId, file));
    }

    @DeleteMapping("/{materialId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> delete(@PathVariable UUID classroomId, @PathVariable UUID materialId,
            Principal principal) {
        service.delete(principal.getName(), classroomId, materialId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{materialId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEACHER', 'STUDENT')")
    public ResponseEntity<Resource> download(@PathVariable UUID classroomId, @PathVariable UUID materialId,
            @RequestParam(defaultValue = "false") boolean inline, Principal principal) {
        ClassroomMaterialService.Download file = service.download(principal.getName(), classroomId, materialId);
        boolean preview = inline && file.previewable();
        ContentDisposition disposition = (preview ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.originalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.resource());
    }
}
