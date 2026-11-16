package com.n0hana.echoes_server.classroom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.infra.file.FileStorageService;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClassroomMaterialService {

    // extensões aceitas no material de aula, vêm do MATERIAL_EXTENSIONS_ALLOWED
    @Value("#{'${MATERIAL_EXTENSIONS_ALLOWED:pdf,docx,jpg,jpeg,png}'.split(',')}")
    private List<String> allowedExtensions;

    private final ClassroomMaterialRepository materialRepository;
    private final ClassroomMemberRepository memberRepository;
    private final ClassroomService classroomService;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    // gestor da instituição ou professor da turma posta o material
    @Transactional
    @Auditable(action = "CREATE_MATERIAL", entity = "ClassroomMaterial", entityId = "#result.id", institutionId = "#result.classroom.institution.id")
    public ClassroomMaterial create(String email, UUID classroomId, ClassroomMaterialDTO.Request dto,
            MultipartFile file) {
        User user = findUser(email);
        ClassroomModel classroom = classroomService.findById(email, classroomId);

        if (!isManagerOf(user, classroom) && !isTeacherMember(user, classroomId))
            throw new ClassroomForbiddenException("Apenas o gestor ou os professores da turma podem postar material");

        if (classroom.isArchived())
            throw new ClassroomArchivedException("Turma arquivada não aceita novas postagens");

        String title = dto.title().trim();
        String fileName = fileStorageService.store(file, safeName(title), classroomId.toString(), allowedExtensions);

        return materialRepository.save(ClassroomMaterial.builder()
                .classroom(classroom)
                .author(user)
                .title(title)
                .topic(dto.topic().trim())
                .fileName(fileName)
                .build());
    }

    // quem enxerga a turma (admin, gestor ou membro) lista o material
    @Transactional(readOnly = true)
    public Page<ClassroomMaterialDTO.Info> findAll(String email, UUID classroomId, String title, String topic,
            Pageable pageable) {
        classroomService.findById(email, classroomId);

        return materialRepository
                .findByClassroomIdAndTitleContainingIgnoreCaseAndTopicContainingIgnoreCase(classroomId, title, topic,
                        pageable)
                .map(ClassroomMaterialDTO.Info::from);
    }

    // mesma regra da listagem
    @Transactional(readOnly = true)
    public ClassroomMaterialDTO.Download download(String email, UUID classroomId, UUID materialId) {
        classroomService.findById(email, classroomId);
        ClassroomMaterial material = findMaterial(classroomId, materialId);

        Path path = fileStorageService.load(classroomId.toString(), material.getFileName());
        if (!Files.exists(path))
            throw new ClassroomNotFoundException("Arquivo não encontrado");

        String extension = material.getFileName().substring(material.getFileName().lastIndexOf("."));
        return new ClassroomMaterialDTO.Download(path, material.getTitle() + extension);
    }

    // só título e tópico mudam, para trocar o arquivo é preciso excluir e postar de novo
    @Transactional
    @Auditable(action = "UPDATE_MATERIAL", entity = "ClassroomMaterial", entityId = "#materialId", institutionId = "#result.classroom.institution.id")
    public ClassroomMaterial update(String email, UUID classroomId, UUID materialId,
            ClassroomMaterialDTO.Request dto) {
        User user = findUser(email);
        ClassroomModel classroom = classroomService.findById(email, classroomId);
        ClassroomMaterial material = findMaterial(classroomId, materialId);

        assertCanChange(user, classroom, material);

        if (classroom.isArchived())
            throw new ClassroomArchivedException();

        material.setTitle(dto.title().trim());
        material.setTopic(dto.topic().trim());
        return materialRepository.save(material);
    }

    // apaga o registro e o arquivo, turma arquivada ainda permite excluir
    @Transactional
    @Auditable(action = "DELETE_MATERIAL", entity = "ClassroomMaterial", entityId = "#materialId", institutionId = "#result.classroom.institution.id")
    public ClassroomMaterial delete(String email, UUID classroomId, UUID materialId) {
        User user = findUser(email);
        ClassroomModel classroom = classroomService.findById(email, classroomId);
        ClassroomMaterial material = findMaterial(classroomId, materialId);

        assertCanChange(user, classroom, material);

        materialRepository.delete(material);
        fileStorageService.delete(classroomId.toString(), material.getFileName());
        return material;
    }

    // gestor altera qualquer postagem da instituição, professor só as próprias e enquanto for da turma
    private void assertCanChange(User user, ClassroomModel classroom, ClassroomMaterial material) {
        boolean author = material.getAuthor().getId().equals(user.getId());

        if (isManagerOf(user, classroom) || (author && isTeacherMember(user, classroom.getId())))
            return;

        throw new ClassroomForbiddenException("Você só pode alterar as suas próprias postagens");
    }

    private boolean isManagerOf(User user, ClassroomModel classroom) {
        return user instanceof Manager manager
                && manager.getInstitution() != null
                && manager.getInstitution().getId().equals(classroom.getInstitution().getId());
    }

    private boolean isTeacherMember(User user, UUID classroomId) {
        return user instanceof Teacher && memberRepository.existsByClassroomIdAndUserId(classroomId, user.getId());
    }

    private ClassroomMaterial findMaterial(UUID classroomId, UUID materialId) {
        return materialRepository.findByIdAndClassroomId(materialId, classroomId)
                .orElseThrow(() -> new ClassroomNotFoundException("Material não encontrado"));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
    }

    // o título vira parte do nome do arquivo: só letras e números, para não sair da pasta da turma
    private String safeName(String title) {
        String name = title.replaceAll("[^\\p{L}\\p{N}]+", "_");
        return name.length() > 50 ? name.substring(0, 50) : name;
    }
}
