package com.rivoo.appointment.infrastructure.adapter.out.persistence.repository;

import com.rivoo.appointment.domain.model.AppointmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.core.TypeInformation;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the return type declared on {@link AppointmentJpaRepository#aggregateByClientAndStatus}
 * against a class of bug that silently breaks the client appointment history: a repository method
 * that returns {@code Object[]} is classified by Spring Data as a "collection query"
 * ({@code TypeInformation.isCollectionLike()} is {@code true} for arrays), so Spring Data executes
 * it as a {@code CollectionExecution} and wraps the single aggregate row one level deeper than the
 * caller assumes ({@code Object[]{ Object[]{count, sum, max} }}). Every read then throws a
 * {@code ClassCastException} when the adapter casts {@code row[0]} to {@code Number}.
 *
 * <p>This test needs no database (Testcontainers/Docker are not available in this environment):
 * it inspects the repository method's declared return type using the very same Spring Data API
 * ({@code TypeInformation}) that decides how the query will be executed. It fails for any
 * collection-like return type (arrays, {@code List}, etc.) and passes for a concrete
 * single-result projection such as {@link AppointmentAggregateProjection}.
 */
class AppointmentJpaRepositoryContractTest {

    @Test
    @DisplayName("aggregateByClientAndStatus must NOT declare a collection-like return type, "
            + "or Spring Data will execute it as a CollectionExecution and nest the aggregate row")
    void aggregateByClientAndStatus_returnTypeIsNotCollectionLike() throws NoSuchMethodException {
        Method method = AppointmentJpaRepository.class.getMethod(
                "aggregateByClientAndStatus", String.class, String.class, AppointmentStatus.class);

        boolean collectionLike = TypeInformation.fromReturnTypeOf(method).isCollectionLike();

        assertThat(collectionLike)
                .as("Spring Data treats array/collection return types as CollectionExecution, "
                        + "which breaks single-row JPQL aggregate projections like this one")
                .isFalse();
    }
}
