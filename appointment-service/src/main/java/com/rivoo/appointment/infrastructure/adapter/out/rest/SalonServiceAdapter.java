package com.rivoo.appointment.infrastructure.adapter.out.rest;

import com.rivoo.appointment.domain.exception.SalonNotFoundException;
import com.rivoo.appointment.domain.port.out.SalonServicePort;
import com.rivoo.appointment.infrastructure.adapter.out.rest.dto.SalonInternalDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class SalonServiceAdapter implements SalonServicePort {

    private final RestClient restClient;

    public SalonServiceAdapter(RestClient.Builder interServiceRestClientBuilder,
                               @Value("${rivoo.services.salon-service.url}") String salonServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(salonServiceUrl)
                .build();
    }

    @Override
    public SalonInfo getSalonBySlug(String slug) {
        log.atInfo().addKeyValue("slug", slug).log("Fetching salon by slug from salon-service");
        try {
            SalonInternalDto dto = restClient.get()
                    .uri("/api/internal/salons/by-slug/{slug}", slug)
                    .retrieve()
                    .body(SalonInternalDto.class);
            if (dto == null) {
                throw new SalonNotFoundException(slug);
            }
            // For salons, external_id == tenant_id (the salon IS the tenant)
            return new SalonInfo(dto.id(), dto.name(), dto.status());
        } catch (HttpClientErrorException.NotFound e) {
            // salon-service legitimately has no salon for this slug. This must
            // surface as a domain "not found" — not as a 500 — so the two
            // anonymous public flows (availability, booking) can turn it into
            // the same response they give for a salon that exists but is not
            // ACTIVE, and an anonymous caller cannot enumerate slugs by
            // distinguishing a 500 (unknown slug) from a 422 (suspended salon).
            log.atWarn().addKeyValue("slug", slug).log("Salon not found in salon-service");
            throw new SalonNotFoundException(slug);
        } catch (SalonNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("slug", slug).log("Failed to fetch salon by slug");
            throw new RuntimeException("Failed to fetch salon from salon-service: " + slug, e);
        }
    }
}
