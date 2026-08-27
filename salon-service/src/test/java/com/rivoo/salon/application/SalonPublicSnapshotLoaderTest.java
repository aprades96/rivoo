package com.rivoo.salon.application;

import com.rivoo.salon.domain.exception.SalonNotFoundException;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the DB-only read step extracted out of
 * {@code SalonService.getPublicBySlug} (see {@link SalonPublicSnapshotLoader}
 * javadoc for why this had to become a separate bean). This is where the
 * ACTIVE-only visibility rule for public salon pages actually lives now.
 */
@ExtendWith(MockitoExtension.class)
class SalonPublicSnapshotLoaderTest {

    private static final String SLUG = "salon-demo";
    private static final String TENANT_ID = "sal_demo";

    @Mock
    private SalonPersistencePort salonPersistencePort;

    @Mock
    private BusinessHoursPersistencePort businessHoursPersistencePort;

    private SalonPublicSnapshotLoader loader;

    @Test
    void loadActiveSalon_activeSalon_returnsSalonAndBusinessHours() {
        loader = new SalonPublicSnapshotLoader(salonPersistencePort, businessHoursPersistencePort);
        Salon salon = activeSalon();
        when(salonPersistencePort.findBySlug(SLUG)).thenReturn(Optional.of(salon));
        List<SalonBusinessHours> hours = List.of(
                SalonBusinessHours.builder()
                        .id(1L).salonId(salon.getId()).dayOfWeek(1).open(true)
                        .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0))
                        .build()
        );
        when(businessHoursPersistencePort.findBySalonId(salon.getId())).thenReturn(hours);

        SalonPublicSnapshot snapshot = loader.loadActiveSalon(SLUG);

        assertThat(snapshot.salon()).isEqualTo(salon);
        assertThat(snapshot.businessHours()).isEqualTo(hours);
    }

    // ── only ACTIVE salons are publicly bookable ──────────────────────────

    @ParameterizedTest
    @EnumSource(value = SalonStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void loadActiveSalon_nonActiveSalon_throwsSalonNotFound(SalonStatus status) {
        loader = new SalonPublicSnapshotLoader(salonPersistencePort, businessHoursPersistencePort);
        Salon salon = activeSalon();
        salon.setStatus(status);
        when(salonPersistencePort.findBySlug(SLUG)).thenReturn(Optional.of(salon));

        assertThatThrownBy(() -> loader.loadActiveSalon(SLUG))
                .isInstanceOf(SalonNotFoundException.class);

        verifyNoInteractions(businessHoursPersistencePort);
    }

    @Test
    void loadActiveSalon_unknownSlug_throwsSalonNotFound() {
        loader = new SalonPublicSnapshotLoader(salonPersistencePort, businessHoursPersistencePort);
        when(salonPersistencePort.findBySlug(SLUG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loader.loadActiveSalon(SLUG))
                .isInstanceOf(SalonNotFoundException.class);

        verifyNoInteractions(businessHoursPersistencePort);
    }

    private Salon activeSalon() {
        return Salon.builder()
                .id(1L)
                .externalId(TENANT_ID)
                .tenantId(TENANT_ID)
                .name("Demo Salon")
                .slug(SLUG)
                .phone("+34600000000")
                .status(SalonStatus.ACTIVE)
                .build();
    }
}
