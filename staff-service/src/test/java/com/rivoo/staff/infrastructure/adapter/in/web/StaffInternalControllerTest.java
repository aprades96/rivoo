package com.rivoo.staff.infrastructure.adapter.in.web;

import com.rivoo.staff.application.dto.EmployeeInternalResponse;
import com.rivoo.staff.application.dto.EmployeePublicResponse;
import com.rivoo.staff.application.dto.ServiceOfferingInternalResponse;
import com.rivoo.staff.application.dto.ServiceOfferingPublicResponse;
import com.rivoo.staff.domain.port.in.GetEmployeeUseCase;
import com.rivoo.staff.domain.port.in.ManageEmployeeWorkingHoursUseCase;
import com.rivoo.staff.domain.port.in.ManageServiceOfferingUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StaffInternalControllerTest {

    @Mock
    private GetEmployeeUseCase getEmployeeUseCase;

    @Mock
    private ManageServiceOfferingUseCase manageServiceOfferingUseCase;

    @Mock
    private ManageEmployeeWorkingHoursUseCase manageWorkingHoursUseCase;

    private MockMvc mockMvc;

    private static final String TENANT_ID = "sal_tenant-A";

    @BeforeEach
    void setUp() {
        StaffInternalController controller = new StaffInternalController(
                getEmployeeUseCase, manageServiceOfferingUseCase, manageWorkingHoursUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── new public listing routes ─────────────────────────────────────────

    @Test
    void listPublicEmployees_bindsTenantIdFromPath_andDelegatesToUseCase() throws Exception {
        EmployeePublicResponse employee = new EmployeePublicResponse(
                "emp_001", "Ana", "Martinez", "Stylist", List.of("svc_haircut"));
        when(getEmployeeUseCase.listPublicByTenant(TENANT_ID)).thenReturn(List.of(employee));

        mockMvc.perform(get("/api/internal/staff/{tenantId}/employees/public", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("emp_001"))
                .andExpect(jsonPath("$[0].firstName").value("Ana"));

        verify(getEmployeeUseCase).listPublicByTenant(TENANT_ID);
        verifyNoInteractions(manageServiceOfferingUseCase);
    }

    @Test
    void listPublicServices_bindsTenantIdFromPath_andDelegatesToUseCase() throws Exception {
        ServiceOfferingPublicResponse service = new ServiceOfferingPublicResponse(
                "svc_haircut", "Haircut", "Classic haircut", 30, new BigDecimal("25.00"), "EUR");
        when(manageServiceOfferingUseCase.listPublicByTenant(TENANT_ID)).thenReturn(List.of(service));

        mockMvc.perform(get("/api/internal/staff/{tenantId}/services/public", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("svc_haircut"))
                .andExpect(jsonPath("$[0].name").value("Haircut"));

        verify(manageServiceOfferingUseCase).listPublicByTenant(TENANT_ID);
        verifyNoInteractions(getEmployeeUseCase);
    }

    // ── the literal "/public" segment must not be shadowed by the pre-existing
    //    variable "/{employeeId}" and "/{serviceId}" routes, and vice versa ──

    @Test
    void getEmployee_existingVariableRoute_stillResolvesToOldMethod_notShadowedByPublicRoute() throws Exception {
        EmployeeInternalResponse employee = new EmployeeInternalResponse(
                "emp_001", "Ana", "Martinez", "ana@salon.com", "+34600000000", "STYLIST", true);
        when(getEmployeeUseCase.getInternal(TENANT_ID, "emp_001")).thenReturn(employee);

        mockMvc.perform(get("/api/internal/staff/{tenantId}/employees/{employeeId}", TENANT_ID, "emp_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("emp_001"))
                .andExpect(jsonPath("$.email").value("ana@salon.com"));

        verify(getEmployeeUseCase).getInternal(TENANT_ID, "emp_001");
        verifyNoInteractions(manageServiceOfferingUseCase);
    }

    @Test
    void getService_existingVariableRoute_stillResolvesToOldMethod_notShadowedByPublicRoute() throws Exception {
        ServiceOfferingInternalResponse service = new ServiceOfferingInternalResponse(
                "svc_haircut", "Haircut", 30, new BigDecimal("25.00"), "EUR", true);
        when(manageServiceOfferingUseCase.getInternal(TENANT_ID, "svc_haircut")).thenReturn(service);

        mockMvc.perform(get("/api/internal/staff/{tenantId}/services/{serviceId}", TENANT_ID, "svc_haircut"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("svc_haircut"));

        verify(manageServiceOfferingUseCase).getInternal(TENANT_ID, "svc_haircut");
        verifyNoInteractions(getEmployeeUseCase);
    }
}
