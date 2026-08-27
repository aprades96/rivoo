package com.rivoo.salon.infrastructure.adapter.in.web;

import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.salon.domain.exception.SalonNotFoundException;
import com.rivoo.salon.domain.port.in.GetSalonUseCase;
import com.rivoo.salon.domain.port.in.ListSalonsUseCase;
import com.rivoo.salon.domain.port.in.ManageBusinessHoursUseCase;
import com.rivoo.salon.domain.port.in.ManageSalonStatusUseCase;
import com.rivoo.salon.domain.port.in.RegisterSalonUseCase;
import com.rivoo.salon.domain.port.in.UpdateSalonUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fixes the @ControllerAdvice ordering bug for real, with both advices wired
 * together the way they are at runtime: with {@link SalonExceptionHandler}
 * (local, {@code @Order(0)}) and rivoo-common's {@link GlobalExceptionHandler}
 * (explicit {@code @Order(Ordered.LOWEST_PRECEDENCE)}, the same numeric floor
 * Spring already assigns by default to an advice with no {@code @Order}) both
 * registered, a {@link SalonNotFoundException} must resolve through the
 * local handler (404 with the specific message) and never fall through to
 * the generic catch-all (500 "An unexpected error occurred").
 * <p>
 * Deliberately registers {@link GlobalExceptionHandler} FIRST and
 * {@link SalonExceptionHandler} SECOND with
 * {@code MockMvcBuilders.standaloneSetup(...).setControllerAdvice(...)} —
 * Spring's {@code ExceptionHandlerExceptionResolver} sorts controller advice
 * beans by {@code @Order} (via {@code AnnotationAwareOrderComparator}), not
 * by the order they were registered/declared in, so this proves the
 * resolution priority comes from the {@code @Order} annotation itself and
 * not from an accidental registration order. Historically (before
 * {@link SalonExceptionHandler} was given its own {@code @Order(0)}), neither
 * handler declared an explicit order and the outcome depended on Spring
 * Boot's internal component-scan vs. autoconfiguration registration order —
 * this test is what pins the resolution to be deterministic regardless of
 * that internal order.
 * <p>
 * A plain unit test of {@code SalonExceptionHandler} alone would not catch a
 * regression here: the bug is in *ordering between the two beans*, which
 * only manifests when both are registered together, as they are at runtime.
 * <p>
 * Also asserts that a non-existent slug and a non-ACTIVE salon (both surface
 * as the very same {@link SalonNotFoundException}, by design — see
 * {@code SalonPublicSnapshotLoader}) produce response bodies that are
 * identical field-for-field EXCEPT for the two fields that are expected to
 * vary by construction: {@code detail} (which embeds the requested slug —
 * the same input the caller supplied, not information about the salon's
 * actual state) and {@code timestamp} (set to {@code Instant.now()}
 * independently on each request). {@code identicalExceptSlugAndTimestamp}
 * below verifies this on the full raw body, not just a handful of
 * hand-picked {@code jsonPath} assertions.
 */
class SalonExceptionHandlerOrderTest {

    private GetSalonUseCase getSalonUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        getSalonUseCase = mock(GetSalonUseCase.class);
        SalonController controller = new SalonController(
                mock(RegisterSalonUseCase.class),
                getSalonUseCase,
                mock(UpdateSalonUseCase.class),
                mock(ManageBusinessHoursUseCase.class),
                mock(ManageSalonStatusUseCase.class),
                mock(ListSalonsUseCase.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new SalonExceptionHandler())
                .build();
    }

    @Test
    void getPublicBySlug_unknownSlug_returns404WithSalonNotFoundBody() throws Exception {
        when(getSalonUseCase.getPublicBySlug(eq("ghost-slug")))
                .thenThrow(new SalonNotFoundException("ghost-slug"));

        mockMvc.perform(get("/api/v1/salons/public/ghost-slug"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Salon not found: ghost-slug"));
    }

    @Test
    void getPublicBySlug_nonActiveSalon_returns404WithSameBodyShapeAsUnknownSlug() throws Exception {
        // A non-ACTIVE salon surfaces as the exact same SalonNotFoundException as a
        // missing one (see SalonPublicSnapshotLoader): from the controller's point of
        // view there is no difference, which is precisely what must not leak.
        when(getSalonUseCase.getPublicBySlug(eq("onboarding-salon")))
                .thenThrow(new SalonNotFoundException("onboarding-salon"));

        mockMvc.perform(get("/api/v1/salons/public/onboarding-salon"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Salon not found: onboarding-salon"))
                // Same fields as the unknown-slug case: no extra "reason" property
                // that would let a client tell the two apart.
                .andExpect(jsonPath("$.title").value("Salon Not Found"))
                .andExpect(jsonPath("$.type").value("https://rivoo.com/errors/salon-not-found"));
    }

    @Test
    void getPublicBySlug_unknownSlugAndNonActiveSalon_bodiesAreIdenticalExceptSlugAndTimestamp() throws Exception {
        // Same underlying slug for both requests so that "detail" (which embeds the
        // slug) also matches — the only thing left free to vary is "timestamp".
        String slug = "misteriosa";

        when(getSalonUseCase.getPublicBySlug(eq(slug)))
                .thenThrow(new SalonNotFoundException(slug));
        String unknownSlugBody = mockMvc.perform(get("/api/v1/salons/public/" + slug))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // A fresh mock (and controller) simulating the OTHER root cause — the slug
        // resolves but the salon is not ACTIVE — collapsing to the exact same
        // exception, per SalonPublicSnapshotLoader's design.
        GetSalonUseCase nonActiveUseCase = mock(GetSalonUseCase.class);
        when(nonActiveUseCase.getPublicBySlug(eq(slug))).thenThrow(new SalonNotFoundException(slug));
        SalonController nonActiveController = new SalonController(
                mock(RegisterSalonUseCase.class),
                nonActiveUseCase,
                mock(UpdateSalonUseCase.class),
                mock(ManageBusinessHoursUseCase.class),
                mock(ManageSalonStatusUseCase.class),
                mock(ListSalonsUseCase.class));
        MockMvc nonActiveMockMvc = MockMvcBuilders.standaloneSetup(nonActiveController)
                .setControllerAdvice(new GlobalExceptionHandler(), new SalonExceptionHandler())
                .build();
        String nonActiveBody = nonActiveMockMvc.perform(get("/api/v1/salons/public/" + slug))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(normalizeTimestamp(unknownSlugBody)).isEqualTo(normalizeTimestamp(nonActiveBody));
    }

    private static String normalizeTimestamp(String body) {
        return body.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "\"timestamp\":\"NORMALIZED\"");
    }
}
