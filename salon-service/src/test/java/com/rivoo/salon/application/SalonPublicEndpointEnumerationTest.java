package com.rivoo.salon.application;

import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.in.ListSalonsUseCase;
import com.rivoo.salon.domain.port.in.ManageBusinessHoursUseCase;
import com.rivoo.salon.domain.port.in.ManageSalonStatusUseCase;
import com.rivoo.salon.domain.port.in.RegisterSalonUseCase;
import com.rivoo.salon.domain.port.in.UpdateSalonUseCase;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.NotificationServicePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import com.rivoo.salon.infrastructure.adapter.in.web.SalonController;
import com.rivoo.salon.infrastructure.adapter.in.web.SalonExceptionHandler;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves — at the HTTP contract level, with the real {@link GlobalExceptionHandler} and
 * {@link SalonExceptionHandler} wired together as they are at runtime, AND with the real
 * {@link SalonService} and the real {@link SalonPublicSnapshotLoader} (the class that
 * actually implements the ACTIVE-only visibility rule, see its javadoc) — that
 * {@code GET /api/v1/salons/public/{slug}} does not let a caller enumerate salons: a slug
 * that does not exist and a slug that exists but is not ACTIVE must produce the exact same
 * response (status, type, title, detail), not just the same HTTP status.
 * <p>
 * The double is deliberately pushed down to {@link SalonPersistencePort} — the port
 * immediately BELOW {@link SalonPublicSnapshotLoader} — instead of mocking
 * {@code GetSalonUseCase} (a port ABOVE the fixed layer, which would let the same
 * hand-thrown exception be stubbed twice and prove nothing). Scenario A makes the fake
 * persistence port answer "no row for this slug" ({@code Optional.empty()}); scenario B
 * makes it answer with a real {@link Salon} row whose status is {@code SUSPENDED}. Both are
 * genuinely different underlying data, so reverting the ACTIVE-only filter inside
 * {@link SalonPublicSnapshotLoader#loadActiveSalon(String)} makes this test fail.
 */
class SalonPublicEndpointEnumerationTest {

    private static final String SLUG = "misteriosa";

    @Test
    void getPublicBySlug_unknownSlugAndSuspendedSalon_produceIdenticalResponseBodies() throws Exception {
        // Scenario A: no row in the database for this slug at all.
        SalonPersistencePort unknownSlugPort = mock(SalonPersistencePort.class);
        when(unknownSlugPort.findBySlug(SLUG)).thenReturn(Optional.empty());
        String unknownSlugBody = mockMvcFor(unknownSlugPort)
                .perform(get("/api/v1/salons/public/" + SLUG))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Scenario B: the slug resolves to a real row, but the salon is SUSPENDED.
        SalonPersistencePort suspendedPort = mock(SalonPersistencePort.class);
        when(suspendedPort.findBySlug(SLUG)).thenReturn(Optional.of(suspendedSalon()));
        String suspendedBody = mockMvcFor(suspendedPort)
                .perform(get("/api/v1/salons/public/" + SLUG))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(normalizeTimestamp(unknownSlugBody)).isEqualTo(normalizeTimestamp(suspendedBody));
    }

    private static Salon suspendedSalon() {
        return Salon.builder()
                .id(1L)
                .externalId("sal_X")
                .tenantId("sal_X")
                .name("Misteriosa")
                .slug(SLUG)
                .status(SalonStatus.SUSPENDED)
                .build();
    }

    /**
     * Wires the real chain — {@link SalonController}, both advices, the real
     * {@link SalonService} and the real {@link SalonPublicSnapshotLoader} — with only
     * {@link SalonPersistencePort} and {@link BusinessHoursPersistencePort} doubled.
     */
    private static MockMvc mockMvcFor(SalonPersistencePort salonPersistencePort) {
        SalonPublicSnapshotLoader loader = new SalonPublicSnapshotLoader(
                salonPersistencePort, mock(BusinessHoursPersistencePort.class));

        SalonService salonService = new SalonService(
                mock(SalonPersistencePort.class),
                mock(BusinessHoursPersistencePort.class),
                mock(StaffServicePort.class),
                mock(SalonDtoMapper.class),
                loader,
                mock(NotificationServicePort.class));

        SalonController controller = new SalonController(
                mock(RegisterSalonUseCase.class),
                salonService,
                mock(UpdateSalonUseCase.class),
                mock(ManageBusinessHoursUseCase.class),
                mock(ManageSalonStatusUseCase.class),
                mock(ListSalonsUseCase.class));

        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new SalonExceptionHandler())
                .build();
    }

    private static String normalizeTimestamp(String body) {
        return body.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "\"timestamp\":\"NORMALIZED\"");
    }
}
