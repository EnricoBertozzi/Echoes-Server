-- MySQL: UUID mapeado como BINARY(16) pelo Hibernate.
-- Não declarar FK aqui: o Flyway roda ANTES do Hibernate ddl-auto=update e,
-- em bancos novos, classroom_model/users podem não existir ainda.
-- O Hibernate cria as FKs após criar as tabelas-base.
-- Turmas são soft-deleted; a limpeza de materiais é explícita.
CREATE TABLE IF NOT EXISTS classroom_materials (
    id BINARY(16) NOT NULL,
    classroom_id BINARY(16) NOT NULL,
    author_id BINARY(16) NOT NULL,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(1000) DEFAULT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(80) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_classroom_materials_stored_name UNIQUE (stored_file_name)
);

CREATE INDEX idx_classroom_materials_classroom ON classroom_materials (classroom_id);
CREATE INDEX idx_classroom_materials_author ON classroom_materials (author_id);
