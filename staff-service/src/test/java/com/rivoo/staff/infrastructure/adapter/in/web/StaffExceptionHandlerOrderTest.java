package com.rivoo.staff.infrastructure.adapter.in.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.staff.domain.exception.AuthServiceException;
import com.rivoo.staff.domain.port.in.CreateEmployeeUseCase;
import com.rivoo.staff.domain.port.in.DeactivateEmployeeUseCase;
import com.rivoo.staff.domain.port.in.GetEmployeeUseCase;
import com.rivoo.staff.domain.port.in.ManageEmployeeServicesUseCase;
import com.rivoo.staff.domain.port.in.ManageEmployeeWorkingHoursUseCase;
import com.rivoo.staff.domain.port.in.UpdateEmployeeUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
 * together the way they are at runtime: with {@link StaffExceptionHandler}
 * (local, {@code @Order(0)}) and rivoo-common's {@link GlobalExceptionHandler}
 * (no {@code @Order}, defaults to {@code Ordered.LOWEST_PRECEDENCE}) both
 * registered, an {@link AuthServiceException} must resolve through the local
 * handler (502 with the specific message and its atError+stack-trace
 * logging) and never fall through to the generic catch-all (500 "An
 * unexpected error occurred").
 * <p>
 * Deliberately registers {@link GlobalExceptionHandler} FIRST and
 * {@link StaffExceptionHandler} SECOND with
 * {@code MockMvcBuilders.standaloneSetup(...).setControllerAdvice(...)} —
 * Spring's {@code ExceptionHandlerExceptionResolver} sorts controller advice
 * beans by {@code @Order} (via {@code AnnotationAwareOrderComparator}), not
 * by the order they were registered/declared in, so this proves the
 * resolution priority comes from the {@code @Order} annotation itself and
 * not from an accidental registration order (which is exactly the bug this
 * task exists to fix: before this change neither handler declared an
 * {@code @Order}, so the outcome depended on Spring Boot's internal
 * component-scan vs. autoconfiguration registration order).
 * <p>
 * A plain unit test of {@code StaffExceptionHandler} alone would not catch a
 * regression here: the bug is in *ordering between the two beans*, which
 * only manifests when both are registered together, as they are at runtime.
 */
class StaffExceptionHandlerOrderTest {

    private GetEmployeeUseCase getEmployeeUseCase;
    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        getEmployeeUseCase = mock(GetEmployeeUseCase.class);
        EmployeeController controller = new EmployeeController(
                mock(CreateEmployeeUseCase.class),
                getEmployeeUseCase,
                mock(UpdateEmployeeUseCase.class),
                mock(DeactivateEmployeeUseCase.class),
                mock(ManageEmployeeWorkingHoursUseCase.class),
                mock(ManageEmployeeServicesUseCase.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new StaffExceptionHandler())
                .build();

        logAppender = new ListAppender<>();
        logAppender.start();
        staffExceptionHandlerLogger().addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        staffExceptionHandlerLogger().detachAppender(logAppender);
    }

    private static Logger staffExceptionHandlerLogger() {
        return (Logger) LoggerFactory.getLogger(StaffExceptionHandler.class);
    }

    @Test
    void getById_authServiceUnreachable_returns502WithAuthServiceErrorBody() throws Exception {
        when(getEmployeeUseCase.getByExternalId(eq("emp_1")))
                .thenThrow(new AuthServiceException("Failed to register employee in auth-service for tenant: sal_A"));

        mockMvc.perform(get("/api/v1/staff/employees/emp_1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.detail").value("Failed to register employee in auth-service for tenant: sal_A"))
                .andExpect(jsonPath("$.title").value("Auth Service Error"))
                .andExpect(jsonPath("$.type").value("https://rivoo.com/errors/auth-service-error"));
    }

    @Test
    void getById_authServiceUnreachable_logsThroughLocalHandlerWithStackTrace() throws Exception {
        // Verifies the guarantee the @Order(0) javadoc on StaffExceptionHandler
        // claims: with both advices registered, StaffExceptionHandler.handleAuthServiceError
        // (atError + setCause, giving a stack trace) is the one that runs — not
        // GlobalExceptionHandler.handleRivooException (atWarn, no setCause, message
        // "Rivoo exception"), which would silently drop the stack trace on an
        // auth-service outage.
        when(getEmployeeUseCase.getByExternalId(eq("emp_1")))
                .thenThrow(new AuthServiceException("Failed to register employee in auth-service for tenant: sal_A"));

        mockMvc.perform(get("/api/v1/staff/employees/emp_1"))
                .andExpect(status().isBadGateway());

        assertThat(logAppender.list)
                .as("the local handler's specific log line must be the one produced")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).isEqualTo("Auth service error");
                    assertThat(event.getThrowableProxy())
                            .as("atError().setCause(ex) must attach the exception, giving a stack trace")
                            .isNotNull();
                });
        assertThat(logAppender.list)
                .as("GlobalExceptionHandler.handleRivooException must NOT be the one that ran")
                .noneMatch(event -> "Rivoo exception".equals(event.getFormattedMessage()));
    }
}
