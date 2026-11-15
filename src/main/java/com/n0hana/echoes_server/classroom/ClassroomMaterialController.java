package com.n0hana.echoes_server.classroom;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.infra.security.SecurityConfig;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/classrooms/{classroomId}/materials")
@SecurityRequirement(name = SecurityConfig.SECURITY)
@RequiredArgsConstructor
public class ClassroomMaterialController {

    private final ClassroomMaterialService service;

    // multipart: parte "dto" com título e tópico (JSON) e parte "file" com o arquivo
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER')")
    public ResponseEntity<ClassroomMaterialDTO.Info> create(@PathVariable UUID classroomId,
            @RequestPart("dto") @Valid ClassroomMaterialDTO.Request dto,
            @RequestPart("file") MultipartFile file, Principal principal) {
        ClassroomMaterial material = service.create(principal.getName(), classroomId, dto, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClassroomMaterialDTO.Info.from(material));
    }

    // mais novas primeiro, filtra por título e por tópico
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEACHER', 'STUDENT')")
    public ResponseEntity<Page<ClassroomMaterialDTO.Info>> findAll(@PathVariable UUID classroomId,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") String topic,
            Principal principal,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.findAll(principal.getName(), classroomId, title, topic, pageable));
    }

    @GetMapping("/{materialId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEACHER', 'STUDENT')")
    public ResponseEntity<Resource> download(@PathVariable UUID classroomId, @PathVariable UUID materialId,
            Principal principal) {
        ClassroomMaterialDTO.Download file = service.download(principal.getName(), classroomId, materialId);

        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(file.name()).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.name(), StandardCharsets.UTF_8).build()
                                .toString())
                .body(new FileSystemResource(file.path()));
    }

    @PutMapping("/{materialId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER')")
    public ResponseEntity<ClassroomMaterialDTO.Info> update(@PathVariable UUID classroomId,
            @PathVariable UUID materialId, @RequestBody @Valid ClassroomMaterialDTO.Request dto,
            Principal principal) {
        ClassroomMaterial material = service.update(principal.getName(), classroomId, materialId, dto);
        return ResponseEntity.ok(ClassroomMaterialDTO.Info.from(material));
    }

    @DeleteMapping("/{materialId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'TEACHER')")
    public ResponseEntity<Void> delete(@PathVariable UUID classroomId, @PathVariable UUID materialId,
            Principal principal) {
        service.delete(principal.getName(), classroomId, materialId);
        return ResponseEntity.noContent().build();
    }
}
