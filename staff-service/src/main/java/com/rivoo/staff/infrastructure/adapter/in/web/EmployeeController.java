package com.rivoo.staff.infrastructure.adapter.in.web;

import com.rivoo.common.tenant.TenantContext;
import com.rivoo.staff.application.dto.AssignServicesRequest;
import com.rivoo.staff.application.dto.CreateEmployeeRequest;
import com.rivoo.staff.application.dto.EmployeeResponse;
import com.rivoo.staff.application.dto.EmployeeServiceResponse;
import com.rivoo.staff.application.dto.UpdateEmployeeRequest;
import com.rivoo.staff.application.dto.WorkingHoursRequest;
import com.rivoo.staff.application.dto.WorkingHoursResponse;
import com.rivoo.staff.domain.port.in.CreateEmployeeUseCase;
import com.rivoo.staff.domain.port.in.DeactivateEmployeeUseCase;
import com.rivoo.staff.domain.port.in.GetEmployeeUseCase;
import com.rivoo.staff.domain.port.in.ManageEmployeeServicesUseCase;
import com.rivoo.staff.domain.port.in.ManageEmployeeWorkingHoursUseCase;
import com.rivoo.staff.domain.port.in.UpdateEmployeeUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/staff/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final CreateEmployeeUseCase createEmployeeUseCase;
    private final GetEmployeeUseCase getEmployeeUseCase;
    private final UpdateEmployeeUseCase updateEmployeeUseCase;
    private final DeactivateEmployeeUseCase deactivateEmployeeUseCase;
    private final ManageEmployeeWorkingHoursUseCase manageWorkingHoursUseCase;
    private final ManageEmployeeServicesUseCase manageEmployeeServicesUseCase;

    @PostMapping
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().log("POST /api/v1/staff/employees");
        EmployeeResponse response = createEmployeeUseCase.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<Page<EmployeeResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            Pageable pageable) {
        log.atInfo().log("GET /api/v1/staff/employees");
        Page<EmployeeResponse> response = getEmployeeUseCase.list(includeInactive, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<EmployeeResponse> getById(@PathVariable String id) {
        log.atInfo().addKeyValue("employeeId", id).log("GET /api/v1/staff/employees");
        EmployeeResponse response = getEmployeeUseCase.getByExternalId(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<EmployeeResponse> update(@PathVariable String id,
                                                    @Valid @RequestBody UpdateEmployeeRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().addKeyValue("employeeId", id).log("PUT /api/v1/staff/employees");
        EmployeeResponse response = updateEmployeeUseCase.update(tenantId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().addKeyValue("employeeId", id).log("DELETE /api/v1/staff/employees");
        deactivateEmployeeUseCase.deactivate(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/working-hours")
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<List<WorkingHoursResponse>> getWorkingHours(@PathVariable String id) {
        log.atInfo().addKeyValue("employeeId", id).log("GET /api/v1/staff/employees/working-hours");
        List<WorkingHoursResponse> response = manageWorkingHoursUseCase.getWorkingHours(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/working-hours")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<List<WorkingHoursResponse>> updateWorkingHours(
            @PathVariable String id,
            @Valid @RequestBody List<WorkingHoursRequest> request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().addKeyValue("employeeId", id).log("PUT /api/v1/staff/employees/working-hours");
        List<WorkingHoursResponse> response = manageWorkingHoursUseCase.updateWorkingHours(tenantId, id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/services")
    @PreAuthorize("hasRole('SALON_OWNER')")
    public ResponseEntity<List<EmployeeServiceResponse>> assignServices(
            @PathVariable String id,
            @Valid @RequestBody AssignServicesRequest request) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.atInfo().addKeyValue("employeeId", id).log("POST /api/v1/staff/employees/services");
        List<EmployeeServiceResponse> response = manageEmployeeServicesUseCase.assignServices(tenantId, id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/services")
    @PreAuthorize("hasAnyRole('SALON_OWNER', 'EMPLOYEE')")
    public ResponseEntity<List<EmployeeServiceResponse>> getAssignedServices(@PathVariable String id) {
        log.atInfo().addKeyValue("employeeId", id).log("GET /api/v1/staff/employees/services");
        List<EmployeeServiceResponse> response = manageEmployeeServicesUseCase.getAssignedServices(id);
        return ResponseEntity.ok(response);
    }
}
