package com.n0hana.echoes_server.classroom;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.n0hana.echoes_server.institution.InstitutionModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

@Entity
@Table
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class classroomModel {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, lenght = 100)
  private String title;

  @Column(lenght = 500)
  private String description;

  @Builder.Default
  @Column(nullable = false)
  private boolean archived = false;

  //a  turma herda a instituição do professor que a criou
  @ManyToOne(fetch = FetchType.LAZY, optinal = false)
  @JoinColumn(name = "institution_id", nullable = false)
  private InstitutionModel institution;

  @CreationTimestamp
  @Column(updatable = false, name = "created_at")
  private LocalDateTime createdAt;

  @CreationTimestamp
  @Column(name = "updatet_at")
  private LocalDateTime updatedAt;

}

