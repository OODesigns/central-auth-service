package com.oodesigns.cas.infrastructure.adapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JooqAuditTransactionTest {

    @Test
    void bindsAuthenticatedActorAndMachineClientInsideTransaction() {
        final UUID userId = UUID.randomUUID();
        final UUID machineClientId = UUID.randomUUID();
        final List<String> executedSql = new ArrayList<>();
        final List<Object[]> bindings = new ArrayList<>();
        final JooqAuditTransaction transaction = new JooqAuditTransaction(
            DSL.using(new MockConnection(recordingProvider(executedSql, bindings)), SQLDialect.POSTGRES),
            () -> new JooqAuditTransaction.AuditActor(userId, machineClientId));

        transaction.execute(context -> context.execute("SELECT protected_mutation()"));

        assertEquals(List.of(
            List.of("app.actor_type", "USER"),
            List.of("app.actor_id", userId.toString()),
            List.of("app.machine_client_id", machineClientId.toString())), bindingValues(bindings));
        assertEquals(List.of("SELECT set_config(?, ?, true)", "SELECT set_config(?, ?, true)",
            "SELECT set_config(?, ?, true)", "SELECT protected_mutation()"), executedSql);
    }

    @Test
    void defaultsToServiceAttributionWithoutAnAuthenticatedPrincipal() {
        final List<String> executedSql = new ArrayList<>();
        final List<Object[]> bindings = new ArrayList<>();
        final JooqAuditTransaction transaction = new JooqAuditTransaction(
            DSL.using(new MockConnection(recordingProvider(executedSql, bindings)), SQLDialect.POSTGRES));

        transaction.execute(context -> context.execute("SELECT service_mutation()"));

        assertEquals(List.of(
            List.of("app.actor_type", "SERVICE"),
            List.of("app.actor_id", ""),
            List.of("app.machine_client_id", "")), bindingValues(bindings));
        assertEquals(List.of("SELECT set_config(?, ?, true)", "SELECT set_config(?, ?, true)",
            "SELECT set_config(?, ?, true)", "SELECT service_mutation()"), executedSql);
    }

    private MockDataProvider recordingProvider(final List<String> executedSql, final List<Object[]> bindings) {
        return context -> {
            executedSql.add(context.sql());
            if (context.sql().startsWith("SELECT set_config")) {
                bindings.add(context.bindings());
            }
            return new MockResult[0];
        };
    }

    private List<List<Object>> bindingValues(final List<Object[]> bindings) {
        return bindings.stream().map(Arrays::asList).toList();
    }
}