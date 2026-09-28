-- Amplia o ENUM da coluna terms.type para aceitar o novo DocumentType
-- PROCESSING_OPERATIONS (Registro das Operações de Tratamento).
--
-- O Hibernate (ddl-auto=update) só ajusta a coluna DEPOIS do Flyway, mas a Java
-- Migration de publicação roda DURANTE o Flyway: se o ENUM ainda não aceitar o
-- valor, o INSERT é rejeitado e a migration falha. Por isso esta migration de
-- estrutura PRECEDE a publicação do documento (V6).
--
-- Os valores legados (DATA_DELETION_POLICY, MARKETING_CONSENT), que não possuem
-- constante em DocumentType, são preservados — o ALTER é aditivo e não apaga
-- documentos já publicados.
ALTER TABLE terms
    MODIFY COLUMN type enum(
        'COOKIES_POLICY',
        'DATA_DELETION_POLICY',
        'MARKETING_CONSENT',
        'PRIVACY_POLICY',
        'TERMS_OF_USE',
        'PROCESSING_OPERATIONS'
    ) DEFAULT NULL;
