package com.rivoo.staff.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rivoo.staff.application.dto.EmployeePublicResponse;
import com.rivoo.staff.domain.model.Employee;
import com.rivoo.staff.domain.model.EmployeeRole;
import com.rivoo.staff.domain.model.EmployeeServiceAssignment;
import com.rivoo.staff.domain.port.out.AuthServicePort;
import com.rivoo.staff.domain.port.out.BillingServicePort;
import com.rivoo.staff.domain.port.out.EmployeePersistencePort;
import com.rivoo.staff.domain.port.out.EmployeeServicePersistencePort;
import com.rivoo.staff.domain.port.out.ServiceOfferingPersistencePort;
import com.rivoo.staff.domain.port.out.WorkingHoursPersistencePort;
import com.rivoo.staff.infrastructure.mapper.EmployeeDtoMapper;
import com.rivoo.staff.infrastructure.mapper.EmployeeDtoMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServicePublicListTest {

    @Mock
    private EmployeePersistencePort employeePersistencePort;

    @Mock
    private WorkingHoursPersistencePort workingHoursPersistencePort;

    @Mock
    private EmployeeServicePersistencePort employeeServicePersistencePort;

    @Mock
    private ServiceOfferingPersistencePort serviceOfferingPersistencePort;

    @Mock
    private AuthServicePort authServicePort;

    @Mock
    private BillingServicePort billingServicePort;

    private EmployeeService employeeService;

    private static final String TENANT_A = "sal_tenant-A";

    @BeforeEach
    void setUp() {
        // Real mapper (not mocked) so we exercise the actual field mapping,
        // including the PII-exclusion check on the record's JSON serialization.
        EmployeeDtoMapper mapper = new EmployeeDtoMapperImpl();
        employeeService = new EmployeeService(
                employeePersistencePort, workingHoursPersistencePort,
                employeeServicePersistencePort, serviceOfferingPersistencePort,
                authServicePort, billingServicePort, mapper);
    }

    // ── the fix this task exists for: explicit tenant filtering ──────────

    @Test
    void listPublicByTenant_callsExplicitTenantFilteredQuery_neverTheUnfilteredOne() {
        Employee employee = buildEmployee("emp_001", TENANT_A);
        when(employeePersistencePort.findAllActiveByTenantId(TENANT_A)).thenReturn(List.of(employee));
        when(employeeServicePersistencePort.findByEmployeeId(employee.getId())).thenReturn(List.of());

        employeeService.listPublicByTenant(TENANT_A);

        verify(employeePersistencePort).findAllActiveByTenantId(TENANT_A);
        verify(employeePersistencePort, never()).findAllActive(org.mockito.ArgumentMatchers.any());
    }

    // ── guard against a missing/blank tenant (anonymous visitor with no context) ──

    @Test
    void listPublicByTenant_blankTenantId_throwsIllegalArgumentException_withoutTouchingPersistence() {
        assertThatThrownBy(() -> employeeService.listPublicByTenant(""))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(employeePersistencePort);
        verifyNoInteractions(employeeServicePersistencePort);
    }

    @Test
    void listPublicByTenant_nullTenantId_throwsIllegalArgumentException_withoutTouchingPersistence() {
        assertThatThrownBy(() -> employeeService.listPublicByTenant(null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(employeePersistencePort);
        verifyNoInteractions(employeeServicePersistencePort);
    }

    // ── no PII leaks to the anonymous visitor ─────────────────────────────

    @Test
    void listPublicByTenant_doesNotExposeEmailOrPhone() throws Exception {
        Employee employee = buildEmployee("emp_002", TENANT_A);
        employee.setEmail("secret.employee@salon-a.com");
        employee.setPhone("+34 600 999 888");

        EmployeeServiceAssignment assignment = EmployeeServiceAssignment.builder()
                .employeeId(employee.getId())
                .serviceExternalId("svc_haircut")
                .build();

        when(employeePersistencePort.findAllActiveByTenantId(TENANT_A)).thenReturn(List.of(employee));
        when(employeeServicePersistencePort.findByEmployeeId(employee.getId())).thenReturn(List.of(assignment));

        List<EmployeePublicResponse> result = employeeService.listPublicByTenant(TENANT_A);

        assertThat(result).hasSize(1);
        EmployeePublicResponse response = result.get(0);
        assertThat(response.id()).isEqualTo("emp_002");
        assertThat(response.serviceIds()).containsExactly("svc_haircut");

        String json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).doesNotContain("secret.employee@salon-a.com");
        assertThat(json).doesNotContain("+34 600 999 888");
        assertThat(json).doesNotContainIgnoringCase("email");
        assertThat(json).doesNotContainIgnoringCase("phone");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Employee buildEmployee(String externalId, String tenantId) {
        return Employee.builder()
                .id(1L)
                .externalId(externalId)
                .tenantId(tenantId)
                .firstName("Ana")
                .lastName("Martinez")
                .jobTitle("Stylist")
                .role(EmployeeRole.STYLIST)
                .active(true)
                .build();
    }
}
