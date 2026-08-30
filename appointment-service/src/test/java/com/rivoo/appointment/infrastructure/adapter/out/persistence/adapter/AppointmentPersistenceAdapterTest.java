package com.rivoo.appointment.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.appointment.domain.model.AppointmentStatus;
import com.rivoo.appointment.domain.port.out.AppointmentPersistencePort.CompletedAppointmentsSummary;
import com.rivoo.appointment.infrastructure.adapter.out.persistence.repository.AppointmentAggregateProjection;
import com.rivoo.appointment.infrastructure.adapter.out.persistence.repository.AppointmentJpaRepository;
import com.rivoo.appointment.infrastructure.mapper.AppointmentPersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit test for the client appointment history aggregate ({@code getCompletedSummaryByClientId}).
 *
 * <p>Prior to this test, the only coverage for {@code row[0..2]} lived in
 * {@code AppointmentRepositoryIntegrationTest}, tagged {@code @Tag("integration")} and excluded
 * from the default build (no Testcontainers/Docker in this environment). This test mocks the
 * repository port so it runs in the default build and exercises the adapter's mapping logic
 * directly, independent of how Spring Data executes the underlying query.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentPersistenceAdapterTest {

    private static final String CLIENT_ID = "cli_test";
    private static final String TENANT_ID = "tenant_test";

    @Mock
    private AppointmentJpaRepository repository;

    @Mock
    private AppointmentPersistenceMapper mapper;

    @InjectMocks
    private AppointmentPersistenceAdapter adapter;

    @Test
    @DisplayName("getCompletedSummaryByClientId — with completed appointments, maps count/sum/max")
    void getCompletedSummaryByClientId_withCompletedAppointments_mapsAllFields() {
        Instant lastCompletedAt = Instant.parse("2026-08-01T10:00:00Z");
        AppointmentAggregateProjection projection =
                new AppointmentAggregateProjection(3L, new BigDecimal("105.00"), lastCompletedAt);
        when(repository.aggregateByClientAndStatus(eq(CLIENT_ID), eq(TENANT_ID), eq(AppointmentStatus.COMPLETED)))
                .thenReturn(projection);

        CompletedAppointmentsSummary summary = adapter.getCompletedSummaryByClientId(CLIENT_ID, TENANT_ID);

        assertThat(summary.completedCount()).isEqualTo(3L);
        assertThat(summary.billedAmount()).isEqualByComparingTo("105.00");
        assertThat(summary.lastCompletedAt()).isEqualTo(lastCompletedAt);
    }

    @Test
    @DisplayName("getCompletedSummaryByClientId — no completed appointments, count=0 and sum/max default")
    void getCompletedSummaryByClientId_noCompletedAppointments_returnsZeroAndDefaults() {
        AppointmentAggregateProjection projection = new AppointmentAggregateProjection(0L, null, null);
        when(repository.aggregateByClientAndStatus(eq(CLIENT_ID), eq(TENANT_ID), any()))
                .thenReturn(projection);

        CompletedAppointmentsSummary summary = adapter.getCompletedSummaryByClientId(CLIENT_ID, TENANT_ID);

        assertThat(summary.completedCount()).isZero();
        assertThat(summary.billedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.lastCompletedAt()).isNull();
    }
}
