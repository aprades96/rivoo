package com.rivoo.staff.application;

import com.rivoo.staff.application.dto.AssignServicesRequest;
import com.rivoo.staff.application.dto.CreateEmployeeRequest;
import com.rivoo.staff.application.dto.EmployeeInternalResponse;
import com.rivoo.staff.application.dto.EmployeePublicResponse;
import com.rivoo.staff.application.dto.EmployeeResponse;
import com.rivoo.staff.application.dto.EmployeeServiceResponse;
import com.rivoo.staff.application.dto.UpdateEmployeeRequest;
import com.rivoo.staff.application.dto.WorkingHoursRequest;
import com.rivoo.staff.application.dto.WorkingHoursResponse;
import com.rivoo.staff.domain.exception.EmployeeLimitExceededException;
import com.rivoo.staff.domain.exception.EmployeeNotFoundException;
import com.rivoo.staff.domain.exception.ServiceOfferingNotFoundException;
import com.rivoo.staff.domain.model.Employee;
import com.rivoo.staff.domain.model.EmployeeRole;
import com.rivoo.staff.domain.model.EmployeeServiceAssignment;
import com.rivoo.staff.domain.model.EmployeeWorkingHours;
import com.rivoo.staff.domain.model.ServiceOffering;
import com.rivoo.staff.domain.port.in.CreateEmployeeUseCase;
import com.rivoo.staff.domain.port.in.DeactivateEmployeeUseCase;
import com.rivoo.staff.domain.port.in.GetEmployeeUseCase;
import com.rivoo.staff.domain.port.in.ManageEmployeeServicesUseCase;
import com.rivoo.staff.domain.port.in.ManageEmployeeWorkingHoursUseCase;
import com.rivoo.staff.domain.port.in.UpdateEmployeeUseCase;
import com.rivoo.staff.domain.port.out.AuthServicePort;
import com.rivoo.staff.domain.port.out.BillingServicePort;
import com.rivoo.staff.domain.port.out.EmployeePersistencePort;
import com.rivoo.staff.domain.port.out.EmployeeServicePersistencePort;
import com.rivoo.staff.domain.port.out.ServiceOfferingPersistencePort;
import com.rivoo.staff.domain.port.out.WorkingHoursPersistencePort;
import com.rivoo.staff.infrastructure.mapper.EmployeeDtoMapper;
import com.rivoo.common.util.ExternalIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService implements CreateEmployeeUseCase, GetEmployeeUseCase,
        UpdateEmployeeUseCase, DeactivateEmployeeUseCase,
        ManageEmployeeWorkingHoursUseCase, ManageEmployeeServicesUseCase {

    private final EmployeePersistencePort employeePersistencePort;
    private final WorkingHoursPersistencePort workingHoursPersistencePort;
    private final EmployeeServicePersistencePort employeeServicePersistencePort;
    private final ServiceOfferingPersistencePort serviceOfferingPersistencePort;
    private final AuthServicePort authServicePort;
    private final BillingServicePort billingServicePort;
    private final EmployeeDtoMapper mapper;

    // ── Create Employee ─────────────────────────────────────────────────

    @Override
    @Transactional
    public EmployeeResponse create(String tenantId, CreateEmployeeRequest request) {
        // Check plan limit
        int maxEmployees = billingServicePort.getMaxEmployees(tenantId);
        if (maxEmployees >= 0) {
            long currentCount = employeePersistencePort.countActiveByTenantId(tenantId);
            if (currentCount >= maxEmployees) {
                throw new EmployeeLimitExceededException(maxEmployees);
            }
        }

        Employee employee = Employee.builder()
                .externalId(ExternalIdGenerator.generate("emp"))
                .tenantId(tenantId)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .jobTitle(request.jobTitle())
                .colorHex(request.colorHex() != null ? request.colorHex() : "#3B82F6")
                .role(request.role() != null ? EmployeeRole.valueOf(request.role()) : EmployeeRole.STYLIST)
                .active(true)
                .build();

        // Optionally register in Keycloak
        if (request.shouldCreateKeycloakAccount() && request.email() != null && request.password() != null) {
            String keycloakUserId = authServicePort.registerEmployee(
                    tenantId, request.email(), request.password(),
                    request.firstName(), request.lastName(), null);
            employee.setKeycloakUserId(keycloakUserId);
        }

        Employee saved = employeePersistencePort.save(employee);

        // Create default working hours (Mon-Fri open, Sat-Sun closed)
        createDefaultWorkingHours(saved.getId());

        log.atInfo().addKeyValue("externalId", saved.getExternalId()).log("Employee created");
        return mapper.toResponse(saved);
    }

    // ── Get Employee ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getByExternalId(String externalId) {
        Employee employee = employeePersistencePort.findByExternalId(externalId)
                .orElseThrow(() -> new EmployeeNotFoundException(externalId));
        return mapper.toResponse(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeResponse> list(Pageable pageable) {
        return employeePersistencePort.findAllActive(pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeInternalResponse getInternal(String tenantId, String employeeExternalId) {
        Employee employee = employeePersistencePort.findByExternalId(employeeExternalId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeExternalId));
        return mapper.toInternalResponse(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeePublicResponse> listPublicByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }

        List<Employee> employees = employeePersistencePort.findAllActiveByTenantId(tenantId);
        return employees.stream()
                .map(employee -> {
                    List<String> serviceIds = employeeServicePersistencePort.findByEmployeeId(employee.getId())
                            .stream()
                            .map(EmployeeServiceAssignment::getServiceExternalId)
                            .toList();
                    return mapper.toPublicResponse(employee, serviceIds);
                })
                .toList();
    }

    // ── Update Employee ─────────────────────────────────────────────────

    @Override
    @Transactional
    public EmployeeResponse update(String tenantId, String externalId, UpdateEmployeeRequest request) {
        Employee employee = employeePersistencePort.findByExternalId(externalId)
                .orElseThrow(() -> new EmployeeNotFoundException(externalId));

        if (request.firstName() != null) employee.setFirstName(request.firstName());
        if (request.lastName() != null) employee.setLastName(request.lastName());
        if (request.email() != null) employee.setEmail(request.email());
        if (request.phone() != null) employee.setPhone(request.phone());
        if (request.jobTitle() != null) employee.setJobTitle(request.jobTitle());
        if (request.colorHex() != null) employee.setColorHex(request.colorHex());
        if (request.role() != null) employee.setRole(EmployeeRole.valueOf(request.role()));

        Employee updated = employeePersistencePort.save(employee);
        log.atInfo().addKeyValue("externalId", externalId).log("Employee updated");
        return mapper.toResponse(updated);
    }

    // ── Deactivate Employee ─────────────────────────────────────────────

    @Override
    @Transactional
    public void deactivate(String tenantId, String externalId) {
        Employee employee = employeePersistencePort.findByExternalId(externalId)
                .orElseThrow(() -> new EmployeeNotFoundException(externalId));

        employee.setActive(false);
        employeePersistencePort.save(employee);
        log.atInfo().addKeyValue("externalId", externalId).log("Employee deactivated");
    }

    // ── Working Hours ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<WorkingHoursResponse> getWorkingHours(String employeeExternalId) {
        Employee employee = employeePersistencePort.findByExternalId(employeeExternalId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeExternalId));

        List<EmployeeWorkingHours> hours = workingHoursPersistencePort.findByEmployeeId(employee.getId());
        return hours.stream().map(mapper::toWorkingHoursResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkingHoursResponse> getWorkingHoursInternal(String tenantId, String employeeExternalId) {
        Employee employee = employeePersistencePort.findByExternalId(employeeExternalId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeExternalId));

        if (!tenantId.equals(employee.getTenantId())) {
            throw new EmployeeNotFoundException(employeeExternalId);
        }

        List<EmployeeWorkingHours> hours = workingHoursPersistencePort.findByEmployeeId(employee.getId());
        return hours.stream().map(mapper::toWorkingHoursResponse).toList();
    }

    @Override
    @Transactional
    public List<WorkingHoursResponse> updateWorkingHours(String tenantId, String employeeExternalId,
                                                          List<WorkingHoursRequest> request) {
        Employee employee = employeePersistencePort.findByExternalId(employeeExternalId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeExternalId));

        workingHoursPersistencePort.deleteByEmployeeId(employee.getId());

        List<EmployeeWorkingHours> hours = request.stream()
                .map(r -> {
                    EmployeeWorkingHours wh = EmployeeWorkingHours.builder()
                            .employeeId(employee.getId())
                            .dayOfWeek(r.dayOfWeek())
                            .open(r.open())
                            .openTime(r.openTime())
                            .closeTime(r.closeTime())
                            .breakStartTime(r.breakStartTime())
                            .breakEndTime(r.breakEndTime())
                            .build();
                    wh.validate();
                    return wh;
                })
                .toList();

        List<EmployeeWorkingHours> saved = workingHoursPersistencePort.saveAll(hours);
        log.atInfo().addKeyValue("externalId", employeeExternalId).log("Working hours updated for employee");
        return saved.stream().map(mapper::toWorkingHoursResponse).toList();
    }

    // ── Employee Services ───────────────────────────────────────────────

    @Override
    @Transactional
    public List<EmployeeServiceResponse> assignServices(String tenantId, String employeeExternalId,
                                                         AssignServicesRequest request) {
        Employee employee = employeePersistencePort.findByExternalId(employeeExternalId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeExternalId));

        // Delete existing assignments and re-create
        employeeServicePersistencePort.deleteByEmployeeId(employee.getId());

        List<EmployeeServiceAssignment> assignments = new ArrayList<>();
        for (AssignServicesRequest.ServiceAssignment sa : request.services()) {
            ServiceOffering service = serviceOfferingPersistencePort.findByExternalId(sa.serviceId())
                    .orElseThrow(() -> new ServiceOfferingNotFoundException(sa.serviceId()));

            assignments.add(EmployeeServiceAssignment.builder()
                    .employeeId(employee.getId())
                    .serviceId(service.getId())
                    .tenantId(tenantId)
                    .customDuration(sa.customDuration())
                    .customPrice(sa.customPrice())
                    .serviceName(service.getName())
                    .defaultDuration(service.getDurationMinutes())
                    .defaultPrice(service.getPrice())
                    .build());
        }

        employeeServicePersistencePort.saveAll(assignments);

        log.atInfo().addKeyValue("externalId", employeeExternalId).addKeyValue("count", assignments.size()).log("Services assigned to employee");

        // Return the freshly loaded assignments (with service data)
        return getAssignedServices(employeeExternalId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeServiceResponse> getAssignedServices(String employeeExternalId) {
        Employee employee = employeePersistencePort.findByExternalId(employeeExternalId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeExternalId));

        List<EmployeeServiceAssignment> assignments = employeeServicePersistencePort.findByEmployeeId(employee.getId());
        return assignments.stream().map(mapper::toEmployeeServiceResponse).toList();
    }

    // ── Private Helpers ─────────────────────────────────────────────────

    private void createDefaultWorkingHours(Long employeeId) {
        List<EmployeeWorkingHours> defaults = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            EmployeeWorkingHours wh = EmployeeWorkingHours.builder()
                    .employeeId(employeeId)
                    .dayOfWeek(day)
                    .open(day <= 5) // Mon-Fri open, Sat-Sun closed
                    .openTime(day <= 5 ? java.time.LocalTime.of(9, 0) : null)
                    .closeTime(day <= 5 ? java.time.LocalTime.of(18, 0) : null)
                    .build();
            defaults.add(wh);
        }
        workingHoursPersistencePort.saveAll(defaults);
    }
}
