package com.rivoo.salon.application;

import com.rivoo.common.exception.BusinessValidationException;
import com.rivoo.common.tenant.TenantContext;
import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.domain.port.in.ListSalonsUseCase;
import com.rivoo.salon.domain.port.in.RegisterSalonUseCase;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import com.rivoo.salon.infrastructure.adapter.in.web.SalonController;
import com.rivoo.salon.infrastructure.adapter.in.web.SalonExceptionHandler;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The schedule path had NO covering test at all, which is exactly why inverting the
 * {@link BusinessValidationException} default silently degraded it while 301 tests stayed green:
 * {@code SalonBusinessHours#validate} is not dead code, {@code SalonService#updateBusinessHours}
 * calls it on every row of {@code PUT /api/v1/salons/me/business-hours}.
 * <p>
 * Wiring: the real {@link SalonController}, the real {@link SalonService} (the class that actually
 * builds the domain objects and calls {@code validate()}), the real domain
 * {@link SalonBusinessHours}, and the real {@link GlobalExceptionHandler} +
 * {@link SalonExceptionHandler} registered as at runtime. Only the persistence ports are doubled,
 * and they sit strictly below the layer under test - no stub can produce these messages, they can
 * only come from the domain object.
 * <p>
 * Lives in {@code com.rivoo.salon.application} only because {@code SalonPublicSnapshotLoader}, a
 * constructor argument of {@link SalonService}, is package-private.
 * <p>
 * The endpoint is {@code hasRole('SALON_OWNER')} and each message describes the row the owner just
 * submitted for their own salon, so publishing it is the point: it is the only thing telling them
 * which of the seven rows to fix.
 */
class BusinessHoursValidationDetailTest {

    private static final String TENANT_ID = "sal_98765432-abcd-ef01-2345-678901234567";
    private static final long SALON_ID = 42L;

    private BusinessHoursPersistencePort businessHoursPersistencePort;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SalonPersistencePort salonPersistencePort = mock(SalonPersistencePort.class);
        businessHoursPersistencePort = mock(BusinessHoursPersistencePort.class);

        when(salonPersistencePort.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(Salon.builder().id(SALON_ID).tenantId(TENANT_ID).build()));

        SalonService salonService = new SalonService(
                salonPersistencePort,
                businessHoursPersistencePort,
                mock(StaffServicePort.class),
                mock(SalonDtoMapper.class),
                mock(SalonPublicSnapshotLoader.class));

        SalonController controller = new SalonController(
                mock(RegisterSalonUseCase.class), salonService, salonService, salonService,
                salonService, mock(ListSalonsUseCase.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new SalonExceptionHandler())
                .build();

        TenantContext.setCurrentTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void closingBeforeOpening_tellsTheOwnerWhichRuleFailed() throws Exception {
        expectPublishedDetail("""
                [{"dayOfWeek":1,"isOpen":true,"openTime":"19:00:00","closeTime":"09:00:00"}]
                """, "closeTime must be after openTime");
    }

    @Test
    void openDayWithoutHours_tellsTheOwnerWhichRuleFailed() throws Exception {
        expectPublishedDetail("""
                [{"dayOfWeek":2,"isOpen":true,"openTime":null,"closeTime":null}]
                """, "Open days must have openTime and closeTime");
    }

    @Test
    void breakEndingBeforeItStarts_tellsTheOwnerWhichRuleFailed() throws Exception {
        expectPublishedDetail("""
                [{"dayOfWeek":3,"isOpen":true,"openTime":"09:00:00","closeTime":"20:00:00",
                  "breakStartTime":"14:00:00","breakEndTime":"13:00:00"}]
                """, "breakEndTime must be after breakStartTime");
    }

    /**
     * A row that passes {@code validate()} must still reach persistence: without this, a mutation
     * that made {@code validate()} throw unconditionally would leave the three tests above green.
     */
    @Test
    void aValidScheduleIsNotRejected() throws Exception {
        when(businessHoursPersistencePort.saveAll(anyList())).thenReturn(List.of());

        mockMvc.perform(put("/api/v1/salons/me/business-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"dayOfWeek":4,"isOpen":true,"openTime":"09:00:00","closeTime":"20:00:00"}]
                                """))
                .andExpect(status().isOk());

        verify(businessHoursPersistencePort).saveAll(anyList());
    }

    /**
     * Pins the one claim that decides whether {@code validate()}'s dayOfWeek branch is reachable
     * over HTTP: {@code @Valid} on a {@code List<BusinessHoursRequest>} body DOES cascade into the
     * elements here, so {@code @Min(1) @Max(7)} answers 400 and the domain never runs. That branch
     * still uses {@code clientSafe} for consistency with its three siblings - it is reachable from
     * any non-HTTP caller of the domain object, and its message only echoes the submitted value -
     * but if this assertion ever flips to 422, the branch has become HTTP-reachable and its
     * decision has to be re-argued rather than assumed.
     */
    @Test
    void dayOfWeekOutOfRange_isRejectedByBeanValidationBeforeTheDomainRuns() throws Exception {
        mockMvc.perform(put("/api/v1/salons/me/business-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"dayOfWeek":9,"isOpen":false}]
                                """))
                .andExpect(status().isBadRequest());

        verify(businessHoursPersistencePort, never()).saveAll(any());
    }

    private void expectPublishedDetail(String requestBody, String expectedDetail) throws Exception {
        mockMvc.perform(put("/api/v1/salons/me/business-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business Validation Failed"))
                .andExpect(jsonPath("$.detail").value(expectedDetail));

        verify(businessHoursPersistencePort, never()).saveAll(any());
    }
}
