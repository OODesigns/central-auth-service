package com.oodesigns.cas.infrastructure.adapter;

import java.util.function.Function;
import org.jooq.DSLContext;

@FunctionalInterface
interface AuditTransaction {
    <T> T execute(Function<DSLContext, T> mutation);
}