package com.n0hana.echoes_server.db.migration;

import org.flywaydb.core.api.migration.Context;

import com.n0hana.echoes_server.term.model.DocumentType;

/**
 * Publica o Registro de Operações de Tratamento 1.0.0.
 */
public class V6__publish_operations_1_0_0 extends LegalDocumentMigration {

    @Override
    public void migrate(Context context) {
        publishDocument(
            context,
            DocumentType.PROCESSING_OPERATIONS,
            "1.0.0",
            "legal/operations/1.0.0.md"
        );
    }
}
