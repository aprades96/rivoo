package com.rivoo.appointment.infrastructure.adapter.out.rest;

import com.rivoo.appointment.application.dto.EmployeeWorkingHoursDto;
import com.rivoo.appointment.domain.port.out.StaffServicePort;
import com.rivoo.appointment.infrastructure.adapter.out.rest.dto.EmployeeInternalDto;
import com.rivoo.appointment.infrastructure.adapter.out.rest.dto.ServiceOfferingInternalDto;
import com.rivoo.appointment.infrastructure.adapter.out.rest.dto.WorkingHoursInternalDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class StaffServiceAdapter implements StaffServicePort {

    private final RestClient restClient;

    public StaffServiceAdapter(RestClient.Builder interServiceRestClientBuilder,
                               @Value("${rivoo.services.staff-service.url}") String staffServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(staffServiceUrl)
                .build();
    }

    @Override
    public StaffEmployeeInfo getEmployee(String tenantId, String employeeExternalId) {
        log.atInfo().addKeyValue("employeeId", employeeExternalId).log("Fetching employee from staff-service");
        try {
            EmployeeInternalDto dto = restClient.get()
                    .uri("/api/internal/staff/{tenantId}/employees/{employeeId}", tenantId, employeeExternalId)
                    .retrieve()
                    .body(EmployeeInternalDto.class);
            if (dto == null) {
                throw new RuntimeException("Employee not found: " + employeeExternalId);
            }
            return new StaffEmployeeInfo(dto.id(), dto.firstName(), dto.lastName(), dto.active());
        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("employeeId", employeeExternalId).log("Failed to fetch employee");
            throw new RuntimeException("Failed to fetch employee from staff-service: " + employeeExternalId, e);
        }
    }

    @Override
    public StaffServiceInfo getService(String tenantId, String serviceExternalId) {
        log.atInfo().addKeyValue("serviceId", serviceExternalId).log("Fetching service from staff-service");
        try {
            ServiceOfferingInternalDto dto = restClient.get()
                    .uri("/api/internal/staff/{tenantId}/services/{serviceId}", tenantId, serviceExternalId)
                    .retrieve()
                    .body(ServiceOfferingInternalDto.class);
            if (dto == null) {
                throw new RuntimeException("Service not found: " + serviceExternalId);
            }
            return new StaffServiceInfo(dto.id(), dto.name(), dto.price(), dto.durationMinutes(), dto.active());
        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("serviceId", serviceExternalId).log("Failed to fetch service");
            throw new RuntimeException("Failed to fetch service from staff-service: " + serviceExternalId, e);
        }
    }

    @Override
    public List<EmployeeWorkingHoursDto> getEmployeeWorkingHours(String tenantId, String employeeExternalId) {
        log.atInfo().addKeyValue("employeeId", employeeExternalId).log("Fetching working hours from staff-service");
        try {
            List<WorkingHoursInternalDto> dtos = restClient.get()
                    .uri("/api/internal/staff/{tenantId}/employees/{employeeId}/working-hours", tenantId, employeeExternalId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (dtos == null) {
                return List.of();
            }
            return dtos.stream()
                    .map(dto -> new EmployeeWorkingHoursDto(
                            dto.dayOfWeek(), dto.isOpen(), dto.openTime(), dto.closeTime(),
                            dto.breakStartTime(), dto.breakEndTime()))
                    .toList();
        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("employeeId", employeeExternalId).log("Failed to fetch working hours");
            throw new RuntimeException("Failed to fetch working hours from staff-service: " + employeeExternalId, e);
        }
    }
}
