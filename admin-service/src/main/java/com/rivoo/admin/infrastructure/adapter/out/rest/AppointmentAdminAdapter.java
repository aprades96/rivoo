package com.rivoo.admin.infrastructure.adapter.out.rest;

import com.rivoo.admin.infrastructure.adapter.out.rest.dto.AppointmentStatsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class AppointmentAdminAdapter {

    private final RestClient restClient;

    public AppointmentAdminAdapter(RestClient.Builder interServiceRestClientBuilder,
                                   @Value("${rivoo.services.appointment-service.url}") String appointmentServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(appointmentServiceUrl)
                .build();
    }

    public AppointmentStatsDto getAppointmentStats(String tenantId) {
        log.atInfo().log("Calling appointment-service GET /api/internal/admin/appointments/stats");

        AppointmentStatsDto stats = restClient.get()
                .uri("/api/internal/admin/appointments/stats?tenantId={tenantId}", tenantId)
                .retrieve()
                .body(AppointmentStatsDto.class);

        log.atInfo().log("appointment-service returned stats");
        return stats;
    }
}
