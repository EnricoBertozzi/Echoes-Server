package com.n0hana.echoes_server.classroom;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.n0hana.echoes_server.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// postagem de material de aula: um arquivo com título e tópico dentro de uma turma
@Entity
@Table(name = "classroom_materials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassroomMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // carregamento imediato: o DTO e a auditoria leem a turma e o autor fora da transação
    @ManyToOne(optional = false)
    @JoinColumn(name = "classroom_id", nullable = false)
    private ClassroomModel classroom;

    // quem postou, o usuário nunca é apagado de verdade (exclusão lógica)
    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 100)
    private String title;

    // agrupa as postagens para facilitar a busca, ex.: "Cardiologia" ou "Prova 1"
    @Column(nullable = false, length = 100)
    private String topic;

    // nome gerado no disco, o arquivo fica em uploads/classrooms/{id da turma}/
    @Column(nullable = false, name = "file_name")
    private String fileName;

    // data da postagem
    @CreationTimestamp
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
