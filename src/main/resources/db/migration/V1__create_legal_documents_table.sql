-- Estrutura da tabela de documentos legais, equivalente ao schema gerado pelo
-- Hibernate para a entidade TermModel (@Table(name = "terms")).
--
-- CREATE TABLE IF NOT EXISTS: preserva bancos já existentes criados pelo
-- Hibernate (ddl-auto=update), nos quais as tabelas já existem sem histórico
-- Flyway.
CREATE TABLE IF NOT EXISTS terms (
    id bigint NOT NULL,
    content text,
    status enum('APPROVED','ARCHIVED','DRAFT','IN_REVIEW','PUBLISHED') DEFAULT NULL,
    timestamp datetime(6) DEFAULT NULL,
    type enum('COOKIES_POLICY','DATA_DELETION_POLICY','MARKETING_CONSENT','PRIVACY_POLICY','TERMS_OF_USE') DEFAULT NULL,
    version varchar(255) DEFAULT NULL,
    PRIMARY KEY (id)
);

-- Sequência usada pelo Hibernate (GenerationType.SEQUENCE -> terms_seq).
-- A linha é semeada pela primeira Java Migration (LegalDocumentMigration),
-- que aloca ids por esta mesma contagem para nunca colidir com o Hibernate.
CREATE TABLE IF NOT EXISTS terms_seq (
    next_val bigint DEFAULT NULL
);