package com.rivoo.client.infrastructure.adapter.out.rest;

import com.rivoo.client.application.dto.ClientAppointmentDto;
import com.rivoo.client.application.dto.ClientAppointmentsResponse;
import com.rivoo.client.domain.port.out.AppointmentServicePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class AppointmentServiceAdapter implements AppointmentServicePort {

    private static final ParameterizedTypeReference<List<AppointmentInternalDto>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public AppointmentServiceAdapter(RestClient.Builder interServiceRestClientBuilder,
                                     @Value("${rivoo.services.appointment-service.url}") String appointmentServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(appointmentServiceUrl)
                .build();
    }

    @Override
    public List<ClientAppointmentDto> getClientAppointments(String clientExternalId, String tenantId) {
        log.atInfo().addKeyValue("clientId", clientExternalId).log("Fetching appointments from appointment-service for GDPR export");
        try {
            List<AppointmentInternalDto> dtos = restClient.get()
                    .uri("/api/internal/admin/appointments/by-client/{clientId}?tenantId={tenantId}",
                            clientExternalId, tenantId)
                    .retrieve()
                    .body(RESPONSE_TYPE);
            if (dtos == null) {
                return List.of();
            }
            return dtos.stream()
                    .map(dto -> new ClientAppointmentDto(
                            dto.id(),
                            dto.serviceName(),
                            dto.employeeName(),
                            dto.startTime(),
                            dto.endTime(),
                            dto.servicePrice(),
                            dto.status()))
                    .toList();
        } catch (Exception e) {
            log.atWarn().setCause(e).addKeyValue("clientId", clientExternalId)
                    .log("Failed to fetch appointments from appointment-service — returning empty list");
            return List.of();
        }
    }

    // Paginated history for the client screen (D38). Deliberately NOT wrapped in try/catch:
    // unlike getClientAppointments above (GDPR export), a failure here must reach the caller
    // as a real error, not degrade into an empty page that looks identical to "no appointments".
    @Override
    public ClientAppointmentsResponse getClientAppointmentsPage(String clientExternalId, String tenantId,
                                                                 int page, int size) {
        log.atInfo().addKeyValue("clientId", clientExternalId).addKeyValue("page", page).addKeyValue("size", size)
                .log("Fetching paginated appointment history from appointment-service");
        AppointmentHistoryInternalDto response = restClient.get()
                .uri("/api/internal/admin/appointments/by-client/{clientId}?tenantId={tenantId}&page={page}&size={size}",
                        clientExternalId, tenantId, page, size)
                .retrieve()
                .body(AppointmentHistoryInternalDto.class);
        Objects.requireNonNull(response,
                () -> "appointment-service returned no body for client history: " + clientExternalId);

        List<ClientAppointmentDto> content = response.content().stream()
                .map(dto -> new ClientAppointmentDto(
                        dto.id(),
                        dto.serviceName(),
                        dto.employeeName(),
                        dto.startTime(),
                        dto.endTime(),
                        dto.servicePrice(),
                        dto.status()))
                .toList();

        ClientAppointmentsResponse.Summary summary = new ClientAppointmentsResponse.Summary(
                response.summary().totalAppointments(),
                response.summary().billedAmount(),
                response.summary().completedCount(),
                response.summary().lastCompletedAt());

        return new ClientAppointmentsResponse(content, response.page(), response.size(),
                response.totalElements(), response.totalPages(), summary);
    }

    private record AppointmentInternalDto(
            String id,
            String clientName,
            String employeeName,
            String serviceName,
            BigDecimal servicePrice,
            Instant startTime,
            Instant endTime,
            String status,
            String source
    ) {}

    private record AppointmentHistoryInternalDto(
            List<AppointmentInternalDto> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            AppointmentHistorySummaryInternalDto summary
    ) {}

    private record AppointmentHistorySummaryInternalDto(
            long totalAppointments,
            BigDecimal billedAmount,
            long completedCount,
            Instant lastCompletedAt
    ) {}
}
