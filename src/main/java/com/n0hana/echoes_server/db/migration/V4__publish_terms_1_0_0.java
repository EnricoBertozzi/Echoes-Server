package com.n0hana.echoes_server.db.migration;

import org.flywaydb.core.api.migration.Context;

import com.n0hana.echoes_server.term.model.DocumentType;

/**
 * Publica os Termos de Uso 1.0.0.
 */
public class V4__publish_terms_1_0_0 extends LegalDocumentMigration {

    @Override
    public void migrate(Context context) {
        publishDocument(
            context,
            DocumentType.TERMS_OF_USE,
            "1.0.0",
            "legal/terms/1.0.0.md"
        );
    }
}
