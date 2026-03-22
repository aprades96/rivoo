package com.rivoo.admin.infrastructure.adapter.out.rest;

import com.rivoo.admin.infrastructure.adapter.out.rest.dto.SalonAdminDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SalonAdminAdapter {

    private final RestClient restClient;

    public SalonAdminAdapter(RestClient.Builder interServiceRestClientBuilder,
                             @Value("${rivoo.services.salon-service.url}") String salonServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(salonServiceUrl)
                .build();
    }

    @SuppressWarnings("unchecked")
    public List<SalonAdminDto> listAllSalons() {
        log.atInfo().log("Calling salon-service GET /api/internal/admin/salons");
        try {
            Map<String, Object> page = restClient.get()
                    .uri("/api/internal/admin/salons?size=1000")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (page == null || !page.containsKey("content")) {
                return List.of();
            }

            List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
            return content.stream()
                    .map(node -> new SalonAdminDto(
                            (String) node.get("id"),
                            (String) node.get("name"),
                            (String) node.get("slug"),
                            (String) node.get("email"),
                            (String) node.get("phone"),
                            (String) node.get("status"),
                            parseInstant(node.get("createdAt"))))
                    .toList();
        } catch (Exception e) {
            log.atWarn().setCause(e).log("Failed to list salons from salon-service");
            return List.of();
        }
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            // Jackson 3.x serializes Instant as an ISO-8601 string when configured with JavaTimeModule
            return Instant.parse(value.toString());
        } catch (Exception e) {
            log.atDebug().addKeyValue("value", value).log("Could not parse createdAt as Instant");
            return null;
        }
    }
}
