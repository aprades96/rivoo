package com.rivoo.staff.application;

import com.rivoo.staff.application.dto.CreateEmployeeRequest;
import com.rivoo.staff.application.dto.EmployeeResponse;
import com.rivoo.staff.domain.exception.EmployeeLimitExceededException;
import com.rivoo.staff.domain.model.Employee;
import com.rivoo.staff.domain.model.EmployeeRole;
import com.rivoo.staff.domain.model.EmployeeWorkingHours;
import com.rivoo.staff.domain.port.out.AuthServicePort;
import com.rivoo.staff.domain.port.out.BillingServicePort;
import com.rivoo.staff.domain.port.out.EmployeePersistencePort;
import com.rivoo.staff.domain.port.out.EmployeeServicePersistencePort;
import com.rivoo.staff.domain.port.out.ServiceOfferingPersistencePort;
import com.rivoo.staff.domain.port.out.WorkingHoursPersistencePort;
import com.rivoo.staff.infrastructure.mapper.EmployeeDtoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

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

    @Mock
    private EmployeeDtoMapper mapper;

    private EmployeeService employeeService;

    private static final String TENANT_ID = "sal_tenant-001";

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeService(
                employeePersistencePort, workingHoursPersistencePort,
                employeeServicePersistencePort, serviceOfferingPersistencePort,
                authServicePort, billingServicePort, mapper);
    }

    // ── create: happy path ───────────────────────────────────────────────

    @Test
    void create_happyPath_savesEmployeeWithGeneratedExternalId() {
        when(billingServicePort.getMaxEmployees(TENANT_ID)).thenReturn(-1); // unlimited
        when(employeePersistencePort.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        when(workingHoursPersistencePort.saveAll(anyList())).thenReturn(List.of());
        when(mapper.toResponse(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            return new EmployeeResponse(e.getExternalId(), e.getFirstName(), e.getLastName(),
                    e.getEmail(), e.getPhone(), e.getRole().name(), e.isActive(),
                    Instant.now(), Instant.now());
        });

        CreateEmployeeRequest request = new CreateEmployeeRequest(
                "Maria", "Garcia", "maria@salon.com", "+34 600 111 222",
                "STYLIST", false, null);

        EmployeeResponse response = employeeService.create(TENANT_ID, request);

        assertThat(response.firstName()).isEqualTo("Maria");
        assertThat(response.lastName()).isEqualTo("Garcia");
        assertThat(response.active()).isTrue();

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeePersistencePort).save(captor.capture());
        Employee saved = captor.getValue();

        assertThat(saved.getExternalId()).startsWith("emp_");
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getRole()).isEqualTo(EmployeeRole.STYLIST);
    }

    @Test
    void create_happyPath_createsDefaultWorkingHours() {
        when(billingServicePort.getMaxEmployees(TENANT_ID)).thenReturn(-1);
        when(employeePersistencePort.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(42L);
            return e;
        });
        when(workingHoursPersistencePort.saveAll(anyList())).thenReturn(List.of());
        when(mapper.toResponse(any())).thenReturn(dummyResponse());

        employeeService.create(TENANT_ID, buildRequest(false));

        ArgumentCaptor<List<EmployeeWorkingHours>> captor = ArgumentCaptor.forClass(List.class);
        verify(workingHoursPersistencePort).saveAll(captor.capture());
        List<EmployeeWorkingHours> hours = captor.getValue();

        // 7 days (Mon–Sun)
        assertThat(hours).hasSize(7);
        // Monday–Friday should be open
        long openDays = hours.stream().filter(EmployeeWorkingHours::isOpen).count();
        assertThat(openDays).isEqualTo(5);
        // Saturday and Sunday closed
        long closedDays = hours.stream().filter(h -> !h.isOpen()).count();
        assertThat(closedDays).isEqualTo(2);
    }

    // ── create: plan limit exceeded ──────────────────────────────────────

    @Test
    void create_planLimitExceeded_throwsEmployeeLimitExceededException() {
        when(billingServicePort.getMaxEmployees(TENANT_ID)).thenReturn(1); // limit = 1
        when(employeePersistencePort.countActiveByTenantId(TENANT_ID)).thenReturn(1L); // already at limit

        assertThatThrownBy(() -> employeeService.create(TENANT_ID, buildRequest(false)))
                .isInstanceOf(EmployeeLimitExceededException.class);

        verify(employeePersistencePort, never()).save(any());
    }

    @Test
    void create_belowPlanLimit_proceeds() {
        when(billingServicePort.getMaxEmployees(TENANT_ID)).thenReturn(3);
        when(employeePersistencePort.countActiveByTenantId(TENANT_ID)).thenReturn(2L); // 2 of 3 used
        when(employeePersistencePort.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(5L);
            return e;
        });
        when(workingHoursPersistencePort.saveAll(anyList())).thenReturn(List.of());
        when(mapper.toResponse(any())).thenReturn(dummyResponse());

        // Must not throw
        employeeService.create(TENANT_ID, buildRequest(false));

        verify(employeePersistencePort).save(any(Employee.class));
    }

    @Test
    void create_unlimitedPlan_skipsCountCheck() {
        when(billingServicePort.getMaxEmployees(TENANT_ID)).thenReturn(-1); // unlimited
        when(employeePersistencePort.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        when(workingHoursPersistencePort.saveAll(anyList())).thenReturn(List.of());
        when(mapper.toResponse(any())).thenReturn(dummyResponse());

        employeeService.create(TENANT_ID, buildRequest(false));

        // countActiveByTenantId should never be called when limit is -1
        verify(employeePersistencePort, never()).countActiveByTenantId(anyString());
    }

    // ── create: with Keycloak account ───────────────────────────────────

    @Test
    void create_withKeycloakAccount_callsAuthServiceAndSetsKeycloakUserId() {
        String keycloakUserId = "kc-user-uuid-001";
        when(billingServicePort.getMaxEmployees(TENANT_ID)).thenReturn(-1);
        when(authServicePort.registerEmployee(
                TENANT_ID, "pedro@salon.com", "Pass1234!",
                "Pedro", "Lopez", null))
                .thenReturn(keycloakUserId);
        when(employeePersistencePort.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(7L);
            return e;
        });
        when(workingHoursPersistencePort.saveAll(anyList())).thenReturn(List.of());
        when(mapper.toResponse(any())).thenReturn(dummyResponse());

        CreateEmployeeRequest request = new CreateEmployeeRequest(
                "Pedro", "Lopez", "pedro@salon.com", "+34 600 333 444",
                "STYLIST", true, "Pass1234!");

        employeeService.create(TENANT_ID, request);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeePersistencePort).save(captor.capture());
        assertThat(captor.getValue().getKeycloakUserId()).isEqualTo(keycloakUserId);
    }

    @Test
    void create_withKeycloakFlagButNoEmail_doesNotCallAuthService() {
        when(billingServicePort.getMaxEmployees(TENANT_ID)).thenReturn(-1);
        when(employeePersistencePort.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(8L);
            return e;
        });
        when(workingHoursPersistencePort.saveAll(anyList())).thenReturn(List.of());
        when(mapper.toResponse(any())).thenReturn(dummyResponse());

        // createKeycloakAccount=true but email is null
        CreateEmployeeRequest request = new CreateEmployeeRequest(
                "Luis", "Perez", null, null, "STYLIST", true, "Pass1234!");

        employeeService.create(TENANT_ID, request);

        verify(authServicePort, never()).registerEmployee(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private CreateEmployeeRequest buildRequest(boolean withKeycloak) {
        return new CreateEmployeeRequest("Ana", "Martinez", "ana@salon.com",
                "+34 600 000 111", "STYLIST", withKeycloak, withKeycloak ? "Pass1234!" : null);
    }

    private EmployeeResponse dummyResponse() {
        return new EmployeeResponse("emp_abc", "Ana", "Martinez",
                "ana@salon.com", null, "STYLIST", true, Instant.now(), Instant.now());
    }
}
