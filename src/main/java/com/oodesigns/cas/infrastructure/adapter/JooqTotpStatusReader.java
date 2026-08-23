package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.UserId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/**
 * JOOQ-based implementation of {@link Ports.TotpStatusReader}.
 * <p>
 * Wraps the existing {@code api_schema.get_totp_status(uuid)} SECURITY DEFINER function
 * so the application layer can ask a simple yes/no question: "is 2FA enabled for this user?".
 * <p>
 * The function returns a row containing only {@code user_id} when {@code verified_at IS NOT NULL};
 * otherwise it returns no row and this adapter returns {@link Optional#empty()}.
 */
public final class JooqTotpStatusReader implements Ports.TotpStatusReader {

    private final DSLContext dsl;

    /**
     * @param dsl jOOQ DSL context used to invoke {@code api_schema.get_totp_status(uuid)}
     */
    public JooqTotpStatusReader(final DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext cannot be null");
    }

    @Override
    public Optional<UserId> check2FAStatus(final UserId userId) {
        return Optional.ofNullable(userId)
            .flatMap(id -> Routines.getTotpStatus(dsl, id.value())
                .map(TotpStatusRecord::userId)
                .map(UserId::of));
    }

    /**
     * Thin hand-written shim that mirrors jOOQ's generated routine classes.
     * <p>
     * Keeps the SQL invocation in one place and makes the adapter easy to stub in tests.
     */
    private static final class Routines {
        static Optional<TotpStatusRecord> getTotpStatus(final DSLContext ctx, final UUID userId) {
            return ctx.fetchOptional("SELECT * FROM api_schema.get_totp_status(?)", userId)
                .map(r -> new TotpStatusRecord(r.get("user_id", UUID.class)));
        }
    }

    private record TotpStatusRecord(UUID userId) { }
}

