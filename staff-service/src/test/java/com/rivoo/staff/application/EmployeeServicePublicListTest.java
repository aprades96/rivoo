package com.rivoo.staff.application;

import com.rivoo.staff.application.dto.EmployeeInternalResponse;
import com.rivoo.staff.application.dto.EmployeePublicResponse;
import com.rivoo.staff.application.dto.WorkingHoursResponse;
import com.rivoo.staff.domain.exception.EmployeeNotFoundException;
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
import tools.jackson.databind.ObjectMapper;

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
    private static final String TENANT_B = "sal_tenant-B";

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
        verify(employeePersistencePort, never()).search(org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
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

    // ── getInternal: cross-tenant regression (block 1) ────────────────────

    @Test
    void getInternal_employeeBelongsToDifferentTenant_throwsNotFound_neverExposesIt() {
        Employee employeeOfTenantB = buildEmployee("emp_from_b", TENANT_B);
        when(employeePersistencePort.findByExternalId("emp_from_b")).thenReturn(java.util.Optional.of(employeeOfTenantB));

        assertThatThrownBy(() -> employeeService.getInternal(TENANT_A, "emp_from_b"))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void getInternal_sameTenant_returnsResponse() {
        Employee employeeOfTenantA = buildEmployee("emp_from_a", TENANT_A);
        when(employeePersistencePort.findByExternalId("emp_from_a")).thenReturn(java.util.Optional.of(employeeOfTenantA));

        EmployeeInternalResponse response = employeeService.getInternal(TENANT_A, "emp_from_a");

        assertThat(response.id()).isEqualTo("emp_from_a");
    }

    @Test
    void getInternal_blankTenantId_throwsIllegalArgumentException_withoutTouchingPersistence() {
        assertThatThrownBy(() -> employeeService.getInternal("", "emp_from_a"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(employeePersistencePort);
    }

    @Test
    void getInternal_nullTenantId_throwsIllegalArgumentException_withoutTouchingPersistence() {
        assertThatThrownBy(() -> employeeService.getInternal(null, "emp_from_a"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(employeePersistencePort);
    }

    // ── getWorkingHoursInternal: cross-tenant regression, same pattern as
    //    getInternal (b412690) but was left without a test of its own ────────

    @Test
    void getWorkingHoursInternal_employeeBelongsToDifferentTenant_throwsNotFound_neverExposesIt() {
        Employee employeeOfTenantB = buildEmployee("emp_from_b", TENANT_B);
        when(employeePersistencePort.findByExternalId("emp_from_b")).thenReturn(java.util.Optional.of(employeeOfTenantB));

        assertThatThrownBy(() -> employeeService.getWorkingHoursInternal(TENANT_A, "emp_from_b"))
                .isInstanceOf(EmployeeNotFoundException.class);

        verifyNoInteractions(workingHoursPersistencePort);
    }

    @Test
    void getWorkingHoursInternal_sameTenant_returnsResponse() {
        Employee employeeOfTenantA = buildEmployee("emp_from_a", TENANT_A);
        when(employeePersistencePort.findByExternalId("emp_from_a")).thenReturn(java.util.Optional.of(employeeOfTenantA));
        when(workingHoursPersistencePort.findByEmployeeId(employeeOfTenantA.getId())).thenReturn(List.of());

        List<WorkingHoursResponse> response = employeeService.getWorkingHoursInternal(TENANT_A, "emp_from_a");

        assertThat(response).isEmpty();
        verify(workingHoursPersistencePort).findByEmployeeId(employeeOfTenantA.getId());
    }

    @Test
    void getWorkingHoursInternal_blankTenantId_throwsIllegalArgumentException_withoutTouchingPersistence() {
        // Same guard as getInternal (consistency, not exploitability: tenantId here is always
        // a @PathVariable and cannot actually be null/blank at runtime).
        assertThatThrownBy(() -> employeeService.getWorkingHoursInternal("", "emp_from_a"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(employeePersistencePort);
        verifyNoInteractions(workingHoursPersistencePort);
    }

    @Test
    void getWorkingHoursInternal_nullTenantId_throwsIllegalArgumentException_withoutTouchingPersistence() {
        assertThatThrownBy(() -> employeeService.getWorkingHoursInternal(null, "emp_from_a"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(employeePersistencePort);
        verifyNoInteractions(workingHoursPersistencePort);
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
