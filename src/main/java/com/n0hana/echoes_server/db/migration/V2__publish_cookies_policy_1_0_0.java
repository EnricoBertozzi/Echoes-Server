package com.n0hana.echoes_server.db.migration;

import org.flywaydb.core.api.migration.Context;

import com.n0hana.echoes_server.term.model.DocumentType;

/**
 * Publica a Política de Cookies 1.0.0.
 */
public class V2__publish_cookies_policy_1_0_0 extends LegalDocumentMigration {

    @Override
    public void migrate(Context context) {
        publishDocument(
            context,
            DocumentType.COOKIES_POLICY,
            "1.0.0",
            "legal/cookies-policy/1.0.0.md"
        );
    }
}
