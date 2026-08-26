package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.infrastructure.grpc.GrpcAuthInterceptor;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** Executes a database mutation with transaction-local audit attribution. */
public final class JooqAuditTransaction implements AuditTransaction {

    private final DSLContext dsl;
    private final AuditActorSupplier actorSupplier;

    public JooqAuditTransaction(final DSLContext dsl) {
        this(dsl, JooqAuditTransaction::currentActor);
    }

    JooqAuditTransaction(final DSLContext dsl, final AuditActorSupplier actorSupplier) {
        this.dsl = Objects.requireNonNull(dsl, "DSLContext cannot be null");
        this.actorSupplier = Objects.requireNonNull(actorSupplier, "Audit actor supplier cannot be null");
    }

    @Override
    public <T> T execute(final Function<DSLContext, T> mutation) {
        Objects.requireNonNull(mutation, "Mutation cannot be null");
        return dsl.transactionResult(configuration -> {
            final DSLContext transactionDsl = DSL.using(configuration);
            final AuditActor actor = actorSupplier.current();
            setLocal(transactionDsl, "app.actor_type", actor.userId() == null ? "SERVICE" : "USER");
            setLocal(transactionDsl, "app.actor_id", actor.userId());
            setLocal(transactionDsl, "app.machine_client_id", actor.machineClientId());
            return mutation.apply(transactionDsl);
        });
    }

    private void setLocal(final DSLContext transactionDsl, final String key, final UUID value) {
        setLocal(transactionDsl, key, value == null ? "" : value.toString());
    }

    private void setLocal(final DSLContext transactionDsl, final String key, final String value) {
        transactionDsl.execute("SELECT set_config(?, ?, true)", key, value);
    }

    private static AuditActor currentActor() {
        final var principal = GrpcAuthInterceptor.principal();
        return new AuditActor(principal == null ? null : principal.value(),
            GrpcAuthInterceptor.machineClientId());
    }

    record AuditActor(UUID userId, UUID machineClientId) {
    }

    @FunctionalInterface
    interface AuditActorSupplier {
        AuditActor current();
    }
}