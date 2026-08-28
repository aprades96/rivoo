package com.rivoo.staff.infrastructure.adapter.in.web;

import com.rivoo.common.exception.BusinessValidationException;
import com.rivoo.common.tenant.TenantContext;
import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.staff.application.EmployeeService;
import com.rivoo.staff.domain.model.Employee;
import com.rivoo.staff.domain.model.EmployeeWorkingHours;
import com.rivoo.staff.domain.port.out.AuthServicePort;
import com.rivoo.staff.domain.port.out.BillingServicePort;
import com.rivoo.staff.domain.port.out.EmployeePersistencePort;
import com.rivoo.staff.domain.port.out.EmployeeServicePersistencePort;
import com.rivoo.staff.domain.port.out.ServiceOfferingPersistencePort;
import com.rivoo.staff.domain.port.out.WorkingHoursPersistencePort;
import com.rivoo.staff.infrastructure.mapper.EmployeeDtoMapper;
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
 * The staff-side twin of salon-service's {@code BusinessHoursValidationDetailTest}, and it existed
 * as little as that one did: {@code EmployeeWorkingHours#validate} is called by
 * {@link EmployeeService#updateWorkingHours} on every row of
 * {@code PUT /api/v1/staff/employees/{id}/working-hours}, and nothing covered it, so inverting the
 * {@link BusinessValidationException} default degraded these four messages invisibly.
 * <p>
 * Wiring: the real {@link EmployeeController}, the real {@link EmployeeService}, the real domain
 * {@link EmployeeWorkingHours} and the real {@link GlobalExceptionHandler} +
 * {@link StaffExceptionHandler}. Only persistence is doubled, strictly below the layer under test.
 * <p>
 * The endpoint is {@code hasRole('SALON_OWNER')} and each message describes the row the owner just
 * submitted for their own employee, so publishing it is the point.
 */
class WorkingHoursValidationDetailTest {

    private static final String TENANT_ID = "sal_98765432-abcd-ef01-2345-678901234567";
    private static final String EMPLOYEE_EXTERNAL_ID = "emp_abc123";
    private static final long EMPLOYEE_ID = 7L;

    private WorkingHoursPersistencePort workingHoursPersistencePort;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EmployeePersistencePort employeePersistencePort = mock(EmployeePersistencePort.class);
        workingHoursPersistencePort = mock(WorkingHoursPersistencePort.class);

        when(employeePersistencePort.findByExternalId(EMPLOYEE_EXTERNAL_ID))
                .thenReturn(Optional.of(Employee.builder()
                        .id(EMPLOYEE_ID)
                        .externalId(EMPLOYEE_EXTERNAL_ID)
                        .tenantId(TENANT_ID)
                        .build()));

        EmployeeService employeeService = new EmployeeService(
                employeePersistencePort,
                workingHoursPersistencePort,
                mock(EmployeeServicePersistencePort.class),
                mock(ServiceOfferingPersistencePort.class),
                mock(AuthServicePort.class),
                mock(BillingServicePort.class),
                mock(EmployeeDtoMapper.class));

        EmployeeController controller = new EmployeeController(
                employeeService, employeeService, employeeService, employeeService,
                employeeService, employeeService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new StaffExceptionHandler())
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
     * See the salon-service twin: {@code @Valid} cascades into the list elements, so
     * {@code @Min(1) @Max(7)} answers 400 and {@code validate()}'s dayOfWeek branch never runs
     * over HTTP. It still opts in, for consistency and for non-HTTP callers of the domain object.
     */
    @Test
    void dayOfWeekOutOfRange_isRejectedByBeanValidationBeforeTheDomainRuns() throws Exception {
        mockMvc.perform(put("/api/v1/staff/employees/{id}/working-hours", EMPLOYEE_EXTERNAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"dayOfWeek":9,"isOpen":false}]
                                """))
                .andExpect(status().isBadRequest());

        verify(workingHoursPersistencePort, never()).saveAll(any());
    }

    /**
     * A row that passes {@code validate()} must still reach persistence: without this, a mutation
     * making {@code validate()} throw unconditionally would leave the assertions above green.
     */
    @Test
    void aValidScheduleIsNotRejected() throws Exception {
        when(workingHoursPersistencePort.saveAll(anyList())).thenReturn(List.of());

        mockMvc.perform(put("/api/v1/staff/employees/{id}/working-hours", EMPLOYEE_EXTERNAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"dayOfWeek":4,"isOpen":true,"openTime":"09:00:00","closeTime":"20:00:00"}]
                                """))
                .andExpect(status().isOk());

        verify(workingHoursPersistencePort).saveAll(anyList());
    }

    private void expectPublishedDetail(String requestBody, String expectedDetail) throws Exception {
        mockMvc.perform(put("/api/v1/staff/employees/{id}/working-hours", EMPLOYEE_EXTERNAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business Validation Failed"))
                .andExpect(jsonPath("$.detail").value(expectedDetail));

        verify(workingHoursPersistencePort, never()).saveAll(any());
    }
}
