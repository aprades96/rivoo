package com.rivoo.salon.infrastructure.adapter.out.rest;

import com.rivoo.salon.domain.port.out.StaffServicePort;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.EmployeePublicDto;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.ServiceOfferingPublicDto;
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
    public List<EmployeePublicInfo> getPublicEmployees(String tenantId) {
        try {
            // Public booking requests are anonymous: TenantContext is empty (no JWT, no
            // tenant_id claim), so InterServiceRestClientConfig's automatic propagation of
            // X-Tenant-Id does not fire. We set it explicitly here.
            // staff-service's listPublicByTenant already filters by the tenantId taken from
            // the path (explicit column filter), so the response is correct even without
            // this header. This header is defense in depth: TenantInterceptor reads it and
            // populates TenantContext, which re-activates Hibernate's tenant @Filter as a
            // second safety net for that request. If a future query on this endpoint forgets
            // the explicit filter, having the header turns that failure mode closed (returns
            // nothing) instead of open (returns every tenant's data).
            List<EmployeePublicDto> employees = restClient.get()
                    .uri("/api/internal/staff/{tenantId}/public/employees", tenantId)
                    .header("X-Tenant-Id", tenantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<EmployeePublicDto>>() {});

            if (employees == null) {
                return List.of();
            }

            return employees.stream()
                    .map(dto -> new EmployeePublicInfo(dto.id(), dto.firstName(), dto.lastName(),
                            dto.jobTitle(), dto.serviceIds()))
                    .toList();
        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("tenantId", tenantId)
                    .log("Failed to fetch public employees from staff-service, returning empty list");
            return List.of();
        }
    }

    @Override
    public List<ServicePublicInfo> getPublicServices(String tenantId) {
        try {
            // See getPublicEmployees for why X-Tenant-Id is set explicitly here.
            List<ServiceOfferingPublicDto> services = restClient.get()
                    .uri("/api/internal/staff/{tenantId}/public/services", tenantId)
                    .header("X-Tenant-Id", tenantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ServiceOfferingPublicDto>>() {});

            if (services == null) {
                return List.of();
            }

            return services.stream()
                    .map(dto -> new ServicePublicInfo(dto.id(), dto.name(), dto.description(),
                            dto.durationMinutes(), dto.price(), dto.currency()))
                    .toList();
        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("tenantId", tenantId)
                    .log("Failed to fetch public services from staff-service, returning empty list");
            return List.of();
        }
    }
}
