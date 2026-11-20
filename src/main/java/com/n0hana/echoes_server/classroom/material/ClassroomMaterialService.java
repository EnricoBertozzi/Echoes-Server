package com.n0hana.echoes_server.classroom.material;

import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.classroom.ClassroomService;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClassroomMaterialService {
    private final ClassroomService classroomService;
    private final ClassroomMaterialRepository repository;
    private final ClassroomMaterialStorage storage;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<ClassroomMaterialDTO.Info> list(String email, UUID classroomId) {
        classroomService.findById(email, classroomId);
        return repository.findByClassroomIdOrderByCreatedAtDesc(classroomId).stream()
                .map(ClassroomMaterialDTO.Info::from).toList();
    }

    @Transactional
    @Auditable(action = "UPLOAD", entity = "ClassroomMaterial", entityId = "#result.id", entityName = "#result.title", institutionId = "#result.classroom.institution.id")
    public ClassroomMaterial create(String email, UUID classroomId, String title, String description, MultipartFile file) {
        ClassroomModel classroom = classroomService.requireTeacherForMaterials(email, classroomId);
        String checkedTitle = validateTitle(title);
        String checkedDescription = validateDescription(description);
        ClassroomMaterialStorage.Stored stored = storage.store(file);
        ClassroomMaterial material = new ClassroomMaterial();
        material.setClassroom(classroom);
        material.setAuthor(users.findByEmail(email).orElseThrow(UserNotFoundException::new));
        material.setTitle(checkedTitle);
        material.setDescription(checkedDescription);
        applyFile(material, stored);
        return repository.save(material);
    }

    @Transactional
    @Auditable(action = "UPDATE", entity = "ClassroomMaterial", entityId = "#materialId", entityName = "#result.title", institutionId = "#result.classroom.institution.id")
    public ClassroomMaterial update(String email, UUID classroomId, UUID materialId, ClassroomMaterialDTO.Update request) {
        classroomService.requireTeacherForMaterials(email, classroomId);
        ClassroomMaterial material = findMaterial(materialId, classroomId);
        material.setTitle(validateTitle(request.title()));
        material.setDescription(validateDescription(request.description()));
        return repository.save(material);
    }

    @Transactional
    @Auditable(action = "REPLACE_FILE", entity = "ClassroomMaterial", entityId = "#materialId", entityName = "#result.title", institutionId = "#result.classroom.institution.id")
    public ClassroomMaterial replace(String email, UUID classroomId, UUID materialId, MultipartFile file) {
        classroomService.requireTeacherForMaterials(email, classroomId);
        ClassroomMaterial material = findMaterial(materialId, classroomId);
        ClassroomMaterialStorage.Stored stored = storage.store(file);
        String previous = material.getStoredFileName();
        applyFile(material, stored);
        ClassroomMaterial result = repository.save(material);
        storage.deleteAfterCommit(List.of(previous));
        return result;
    }

    @Transactional
    @Auditable(action = "DELETE", entity = "ClassroomMaterial", entityId = "#materialId", entityName = "#result.title", institutionId = "#result.classroom.institution.id")
    public ClassroomMaterial delete(String email, UUID classroomId, UUID materialId) {
        classroomService.requireTeacherForMaterials(email, classroomId);
        ClassroomMaterial material = findMaterial(materialId, classroomId);
        repository.delete(material);
        storage.deleteAfterCommit(List.of(material.getStoredFileName()));
        return material;
    }

    public record Download(Resource resource, String originalName, String contentType,
                           boolean previewable, UUID institutionId) {}

    @Transactional(readOnly = true)
    @Auditable(action = "DOWNLOAD", entity = "ClassroomMaterial", entityId = "#materialId", institutionId = "#result.institutionId")
    public Download download(String email, UUID classroomId, UUID materialId) {
        classroomService.findById(email, classroomId);
        ClassroomMaterial material = findMaterial(materialId, classroomId);
        MaterialFileType type = MaterialFileType.fromFilename(material.getOriginalFileName());
        return new Download(storage.load(material.getStoredFileName()), material.getOriginalFileName(),
                material.getContentType(), type.previewable(), material.getClassroom().getInstitution().getId());
    }

    private ClassroomMaterial findMaterial(UUID materialId, UUID classroomId) {
        return repository.findByIdAndClassroomId(materialId, classroomId).orElseThrow(MaterialException::notFound);
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank() || title.trim().length() > 150)
            throw MaterialException.invalid("Título obrigatório (até 150 caracteres)");
        return title.trim();
    }

    private static String validateDescription(String description) {
        if (description != null && description.length() > 1000)
            throw MaterialException.invalid("Descrição com mais de 1000 caracteres");
        return description == null ? "" : description.trim();
    }

    private static void applyFile(ClassroomMaterial material, ClassroomMaterialStorage.Stored stored) {
        material.setOriginalFileName(stored.originalName());
        material.setStoredFileName(stored.name());
        material.setContentType(stored.contentType());
        material.setFileSize(stored.size());
    }
}
