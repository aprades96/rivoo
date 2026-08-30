package com.rivoo.appointment.infrastructure.adapter.out.persistence.repository;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dedicated JPQL constructor-expression projection for
 * {@link AppointmentJpaRepository#aggregateByClientAndStatus}.
 *
 * <p>Using a concrete return type here (instead of {@code Object[]}) matters: Spring
 * Data classifies a repository method's return type as a "collection query" whenever
 * {@code TypeInformation.isCollectionLike()} is {@code true} for that type — and arrays
 * (including {@code Object[]}) are collection-like. A method declared to return
 * {@code Object[]} is therefore executed as a {@code CollectionExecution}
 * ({@code getResultList()}), and the single-row JPQL aggregate result ends up wrapped
 * as {@code Object[]{ Object[]{count, sum, max} }} instead of the flat row the caller
 * expects, causing a {@code ClassCastException} on every call.
 *
 * <p>A concrete class (record) built via a JPQL {@code new} expression is not
 * collection-like, so Spring Data correctly treats the query as a single-result query.
 */
public record AppointmentAggregateProjection(Long completedCount, BigDecimal billedAmount, Instant lastCompletedAt) {
}
