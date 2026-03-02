package com.rivoo.staff.infrastructure.adapter.in.web;

import com.rivoo.staff.application.dto.EmployeeInternalResponse;
import com.rivoo.staff.application.dto.ServiceOfferingInternalResponse;
import com.rivoo.staff.domain.port.in.GetEmployeeUseCase;
import com.rivoo.staff.domain.port.in.ManageServiceOfferingUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/internal/staff")
@RequiredArgsConstructor
public class StaffInternalController {

    private final GetEmployeeUseCase getEmployeeUseCase;
    private final ManageServiceOfferingUseCase manageServiceOfferingUseCase;

    @GetMapping("/{tenantId}/employees/{employeeId}")
    public ResponseEntity<EmployeeInternalResponse> getEmployee(
            @PathVariable String tenantId,
            @PathVariable String employeeId) {
        log.atInfo().addKeyValue("employeeId", employeeId).log("GET /api/internal/staff/employees");
        EmployeeInternalResponse response = getEmployeeUseCase.getInternal(tenantId, employeeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tenantId}/services/{serviceId}")
    public ResponseEntity<ServiceOfferingInternalResponse> getService(
            @PathVariable String tenantId,
            @PathVariable String serviceId) {
        log.atInfo().addKeyValue("serviceId", serviceId).log("GET /api/internal/staff/services");
        ServiceOfferingInternalResponse response = manageServiceOfferingUseCase.getInternal(tenantId, serviceId);
        return ResponseEntity.ok(response);
    }
}
