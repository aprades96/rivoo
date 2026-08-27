package com.rivoo.appointment.application;

import com.rivoo.appointment.application.dto.AvailabilityResponse;
import com.rivoo.appointment.domain.exception.SalonNotFoundException;
import com.rivoo.appointment.domain.port.out.AppointmentPersistencePort;
import com.rivoo.appointment.domain.port.out.SalonServicePort;
import com.rivoo.appointment.domain.port.out.StaffServicePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The public availability endpoint (GET /api/v1/appointments/public/availability) must resolve
 * the tenant from the salon slug — exactly like the existing public booking flow does — before
 * delegating to the already-tested slot calculation in {@link AvailabilityService}.
 *
 * <p>{@link AvailabilityService} is the actual implementer of {@code CheckAvailabilityUseCase}
 * (not {@code AppointmentService}), so these tests exercise it directly and verify tenant
 * resolution through its real collaborator {@link StaffServicePort}, which is the first port
 * called after slug resolution.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AvailabilityService — public availability resolved by salon slug")
class PublicAvailabilityTest {

    private static final String SALON_SLUG = "bella-vista";
    private static final String RESOLVED_TENANT_ID = "sal_A";
    private static final String EMPLOYEE_ID = "emp_1";
    private static final String SERVICE_ID = "svc_1";
    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);

    @Mock
    private AppointmentPersistencePort appointmentPersistencePort;

    @Mock
    private StaffServicePort staffServicePort;

    @Mock
    private SalonServicePort salonServicePort;

    @InjectMocks
    private AvailabilityService service;

    @Test
    @DisplayName("resolves tenantId from the salon slug and delegates to the existing slot calculation")
    void publicAvailability_resolvesTenantFromSlugAndDelegates() {
        when(salonServicePort.getSalonBySlug(SALON_SLUG))
                .thenReturn(new SalonServicePort.SalonInfo(RESOLVED_TENANT_ID, "Bella Vista", "ACTIVE"));
        when(staffServicePort.getEmployeeWorkingHours(RESOLVED_TENANT_ID, EMPLOYEE_ID))
                .thenReturn(List.of());

        AvailabilityResponse response =
                service.getPublicAvailableSlots(SALON_SLUG, EMPLOYEE_ID, DATE, SERVICE_ID);

        // The tenantId resolved from the slug is the one that reaches the slot calculation —
        // not the raw slug, not an empty TenantContext.
        verify(staffServicePort).getEmployeeWorkingHours(RESOLVED_TENANT_ID, EMPLOYEE_ID);
        assertThat(response.employeeId()).isEqualTo(EMPLOYEE_ID);
        assertThat(response.date()).isEqualTo(DATE);
    }

    @Test
    @DisplayName("rejects a salon that is not ACTIVE without touching the slot calculation, " +
            "with the same exception an unknown slug would raise")
    void publicAvailability_rejectsInactiveSalon() {
        when(salonServicePort.getSalonBySlug("suspendido"))
                .thenReturn(new SalonServicePort.SalonInfo("sal_B", "Suspendido", "SUSPENDED"));

        // Must be SalonNotFoundException — the very same exception SalonServiceAdapter
        // throws for a slug salon-service has never heard of — so an anonymous caller
        // cannot tell "suspended" apart from "never existed" (enumeration oracle).
        assertThatThrownBy(() ->
                service.getPublicAvailableSlots("suspendido", EMPLOYEE_ID, LocalDate.now(), SERVICE_ID))
                .isInstanceOf(SalonNotFoundException.class);

        verifyNoInteractions(staffServicePort);
    }

    @Test
    @DisplayName("same slug: 'does not exist' and 'exists but suspended' raise byte-for-byte " +
            "indistinguishable exceptions (the actual enumeration-oracle property)")
    void publicAvailability_notFoundAndSuspended_areIndistinguishable() {
        String slug = "misteriosa";

        // Scenario A: mirrors what SalonServiceAdapter throws for a slug salon-service has
        // never heard of (a real 404 mapped to SalonNotFoundException).
        SalonServicePort notFoundPort = mock(SalonServicePort.class);
        when(notFoundPort.getSalonBySlug(slug)).thenThrow(new SalonNotFoundException(slug));
        AvailabilityService notFoundService =
                new AvailabilityService(appointmentPersistencePort, staffServicePort, notFoundPort);

        // Scenario B: the slug resolves fine but the salon is not ACTIVE.
        SalonServicePort suspendedPort = mock(SalonServicePort.class);
        when(suspendedPort.getSalonBySlug(slug))
                .thenReturn(new SalonServicePort.SalonInfo("sal_X", "Misteriosa", "SUSPENDED"));
        AvailabilityService suspendedService =
                new AvailabilityService(appointmentPersistencePort, staffServicePort, suspendedPort);

        SalonNotFoundException notFoundException = catchThrowableOfType(
                () -> notFoundService.getPublicAvailableSlots(slug, EMPLOYEE_ID, DATE, SERVICE_ID),
                SalonNotFoundException.class);
        SalonNotFoundException suspendedException = catchThrowableOfType(
                () -> suspendedService.getPublicAvailableSlots(slug, EMPLOYEE_ID, DATE, SERVICE_ID),
                SalonNotFoundException.class);

        // Everything that GlobalExceptionHandler.handleRivooException(RivooException) copies
        // into the HTTP response body must match, not just the exception's runtime type.
        assertThat(notFoundException).isNotNull();
        assertThat(suspendedException).isNotNull();
        assertThat(suspendedException.getClass()).isEqualTo(notFoundException.getClass());
        assertThat(suspendedException.getMessage()).isEqualTo(notFoundException.getMessage());
        assertThat(suspendedException.getHttpStatus()).isEqualTo(notFoundException.getHttpStatus());
        assertThat(suspendedException.getErrorType()).isEqualTo(notFoundException.getErrorType());
        assertThat(suspendedException.getErrorTitle()).isEqualTo(notFoundException.getErrorTitle());
    }
}
