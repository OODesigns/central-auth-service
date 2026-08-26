package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import org.jooq.DSLContext;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Retrieves active machine-client certificate records through the API schema. */
public final class JooqTrustedClientRetriever implements Ports.TrustedClientRetriever {
    private static final String SQL = "SELECT * FROM api_schema.find_trusted_client_by_fingerprint(?)";
    private final DSLContext dsl;

    public JooqTrustedClientRetriever(final DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext cannot be null");
    }

    @Override
    public Optional<Ports.TrustedClient> findByFingerprint(final String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return Optional.empty();
        }
        return dsl.fetchOptional(SQL, fingerprint).map(record -> new Ports.TrustedClient(
                record.get("id", UUID.class),
                record.get("fingerprint", String.class),
                record.get("expires_at", Instant.class),
                record.get("revoked_at", Instant.class)));
    }
}