package com.rivoo.salon.infrastructure.adapter.out.rest;

import com.rivoo.common.web.RivooHeaders;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.EmployeePublicDto;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.ServiceOfferingPublicDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

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
    public Optional<List<EmployeePublicInfo>> getPublicEmployees(String tenantId) {
        List<EmployeePublicDto> employees;
        try {
            // Public booking requests are anonymous: TenantContext is empty (no JWT, no
            // tenant_id claim), so InterServiceRestClientConfig's automatic propagation of
            // X-Tenant-Id does not fire. We set it explicitly here.
            // Note: if this port were ever invoked from an authenticated flow where
            // TenantContext IS populated (e.g. an owner of tenant A calling with a
            // different tenantId), headerPropagationInterceptor unconditionally
            // overwrites this explicit header with TenantContext's value. The
            // effective tenant queried would then be the caller's own tenant, not the
            // argument passed here — fail-closed (no cross-tenant leak), but it
            // contradicts a literal reading of "this header carries the argument's
            // tenantId" for that scenario.
            // staff-service's listPublicByTenant already filters by the tenantId taken from
            // the path (explicit column filter), so the response is correct even without
            // this header. This header is defense in depth: TenantInterceptor reads it and
            // populates TenantContext, which re-activates Hibernate's tenant @Filter as a
            // second safety net for that request. If a future query on this endpoint forgets
            // the explicit filter, having the header turns that failure mode closed (returns
            // nothing) instead of open (returns every tenant's data).
            employees = restClient.get()
                    .uri("/api/internal/staff/{tenantId}/public/employees", tenantId)
                    .header(RivooHeaders.TENANT_ID, tenantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<EmployeePublicDto>>() {});
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("Failed to fetch public employees from staff-service, degrading");
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            log.atError()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("staff-service rejected public employees request with a client error, degrading");
            return Optional.empty();
        } catch (RestClientException e) {
            // Backstop for a 2xx response whose body we cannot use: wrong Content-Type
            // (UnknownContentTypeException, e.g. an edge/proxy error page), a body cut
            // short mid-stream, or a shape mismatch (object where an array is expected).
            // The last one is the classic symptom of a rolling deploy where staff-service
            // shipped a DTO change before salon-service did. This is not a clean HTTP
            // status we can classify with certainty as "us" vs "them", so it is treated
            // like the 5xx/network case: log and degrade instead of taking down the
            // public booking page over what is normally a transient version-skew window.
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("Could not read public employees response body from staff-service, degrading");
            return Optional.empty();
        }

        if (employees == null) {
            // A 2xx with a null body (empty body, literal JSON "null", or 204 No
            // Content) is not staff-service telling us "no employees": it is an
            // unreadable/absent body, same family as the RestClientException case
            // above. Per StaffServicePort's contract, that must be Optional.empty(),
            // not a present-but-empty list.
            log.atWarn()
                    .addKeyValue("targetTenantId", tenantId)
                    .log("staff-service returned a 2xx with a null/absent body for public employees, degrading");
            return Optional.empty();
        }

        return Optional.of(employees.stream()
                .map(dto -> new EmployeePublicInfo(dto.id(), dto.firstName(), dto.lastName(),
                        dto.jobTitle(), dto.serviceIds()))
                .toList());
    }

    @Override
    public Optional<List<ServicePublicInfo>> getPublicServices(String tenantId) {
        List<ServiceOfferingPublicDto> services;
        try {
            // See getPublicEmployees for why X-Tenant-Id is set explicitly here, and for
            // how headerPropagationInterceptor can override it for authenticated callers.
            services = restClient.get()
                    .uri("/api/internal/staff/{tenantId}/public/services", tenantId)
                    .header(RivooHeaders.TENANT_ID, tenantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ServiceOfferingPublicDto>>() {});
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("Failed to fetch public services from staff-service, degrading");
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            log.atError()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("staff-service rejected public services request with a client error, degrading");
            return Optional.empty();
        } catch (RestClientException e) {
            // See getPublicEmployees for why a body we cannot read (bad Content-Type,
            // truncated body, or object/array shape mismatch from a rolling deploy) is
            // treated as a WARN-level degradation rather than propagated as a 500.
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("Could not read public services response body from staff-service, degrading");
            return Optional.empty();
        }

        if (services == null) {
            // See getPublicEmployees for why a null body (empty body, literal JSON
            // "null", or 204 No Content) must degrade instead of being read as "no
            // services exist".
            log.atWarn()
                    .addKeyValue("targetTenantId", tenantId)
                    .log("staff-service returned a 2xx with a null/absent body for public services, degrading");
            return Optional.empty();
        }

        return Optional.of(services.stream()
                .map(dto -> new ServicePublicInfo(dto.id(), dto.name(), dto.description(),
                        dto.durationMinutes(), dto.price(), dto.currency()))
                .toList());
    }
}
