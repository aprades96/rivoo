package com.rivoo.staff.infrastructure.adapter.in.web;

import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.staff.domain.port.in.CreateEmployeeUseCase;
import com.rivoo.staff.domain.port.in.DeactivateEmployeeUseCase;
import com.rivoo.staff.domain.port.in.GetEmployeeUseCase;
import com.rivoo.staff.domain.port.in.ManageEmployeeServicesUseCase;
import com.rivoo.staff.domain.port.in.ManageEmployeeWorkingHoursUseCase;
import com.rivoo.staff.domain.port.in.UpdateEmployeeUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D16b: {@code AssignServicesRequest.services} moves from {@code @NotEmpty} to
 * {@code @NotNull}, so {@code POST /api/v1/staff/employees/{id}/services} with
 * {@code { "services": [] }} becomes a legitimate "unassign everything" request
 * (200, not 400) -- see IMPLEMENTATION_PLAN.md §1.11.5 and §2.9 D16b.
 * <p>
 * A missing {@code services} field must still be rejected: {@code @NotNull} is
 * kept, only emptiness is now allowed.
 */
class EmployeeAssignServicesValidationTest {

    private ManageEmployeeServicesUseCase manageEmployeeServicesUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        manageEmployeeServicesUseCase = mock(ManageEmployeeServicesUseCase.class);
        EmployeeController controller = new EmployeeController(
                mock(CreateEmployeeUseCase.class),
                mock(GetEmployeeUseCase.class),
                mock(UpdateEmployeeUseCase.class),
                mock(DeactivateEmployeeUseCase.class),
                mock(ManageEmployeeWorkingHoursUseCase.class),
                manageEmployeeServicesUseCase);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new StaffExceptionHandler())
                .build();
    }

    @Test
    void assignServices_emptyServicesList_isAccepted_andClearsAssignments() throws Exception {
        when(manageEmployeeServicesUseCase.assignServices(anyString(), anyString(), any()))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/v1/staff/employees/emp_1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"services\": []}"))
                .andExpect(status().isOk());

        verify(manageEmployeeServicesUseCase).assignServices(any(), org.mockito.ArgumentMatchers.eq("emp_1"),
                org.mockito.ArgumentMatchers.argThat(req -> req.services().isEmpty()));
    }

    @Test
    void assignServices_missingServicesField_isStillRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/staff/employees/emp_1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
