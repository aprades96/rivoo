package com.rivoo.salon.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.salon.infrastructure.adapter.out.persistence.repository.SalonJpaRepository;
import com.rivoo.salon.infrastructure.mapper.SalonPersistenceMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link SalonPersistenceAdapter#markOnboardingCompleted(String)} to genuinely delegating to
 * {@link SalonJpaRepository#markOnboardingCompletedIfPending(String, Instant)} rather than, say,
 * always returning {@code 0} without calling the repository at all - a mutation that a panel of
 * reviewers found left the wider test suite green, because {@link SalonOnboardingCompletionTest} in
 * the {@code application} package exercises a hand-written in-memory fake, never this adapter.
 * <p>
 * Deliberately a plain Mockito unit test, not the MySQL-backed
 * {@code SalonJpaRepositoryOnboardingCompletionIntegrationTest}: that one proves the JPQL predicate
 * is enforced by the database; this one proves the adapter method between the port and that JPQL is
 * not a no-op. The two are complementary, not redundant.
 */
class SalonPersistenceAdapterOnboardingCompletionTest {

    private static final String TENANT_ID = "sal_onboarding_done";

    @Test
    void delegatesToTheConditionalJpqlUpdateAndReturnsItsResult() {
        SalonJpaRepository repository = mock(SalonJpaRepository.class);
        SalonPersistenceMapper mapper = mock(SalonPersistenceMapper.class);
        when(repository.markOnboardingCompletedIfPending(eq(TENANT_ID), any(Instant.class)))
                .thenReturn(1);
        SalonPersistenceAdapter adapter = new SalonPersistenceAdapter(repository, mapper);

        int updatedRows = adapter.markOnboardingCompleted(TENANT_ID);

        assertThat(updatedRows)
                .as("the adapter must return whatever the JPQL update actually changed, not a value of its own")
                .isEqualTo(1);
        verify(repository, times(1))
                .markOnboardingCompletedIfPending(eq(TENANT_ID), any(Instant.class));
    }

    @Test
    void returnsZeroWhenTheRepositoryFoundNothingToUpdate() {
        SalonJpaRepository repository = mock(SalonJpaRepository.class);
        SalonPersistenceMapper mapper = mock(SalonPersistenceMapper.class);
        when(repository.markOnboardingCompletedIfPending(eq(TENANT_ID), any(Instant.class)))
                .thenReturn(0);
        SalonPersistenceAdapter adapter = new SalonPersistenceAdapter(repository, mapper);

        int updatedRows = adapter.markOnboardingCompleted(TENANT_ID);

        assertThat(updatedRows).isZero();
        verify(repository, times(1))
                .markOnboardingCompletedIfPending(eq(TENANT_ID), any(Instant.class));
    }
}
