package com.rivoo.appointment.infrastructure.adapter.out.rest;

import com.rivoo.appointment.domain.exception.SalonNotFoundException;
import com.rivoo.appointment.domain.exception.SalonServiceUnavailableException;
import com.rivoo.appointment.domain.port.out.SalonServicePort;
import com.rivoo.appointment.infrastructure.adapter.out.rest.dto.SalonInternalDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Slf4j
@Component
public class SalonServiceAdapter implements SalonServicePort {

    // The `type` salon-service's own SalonExceptionHandler sets on the ProblemDetail body of
    // its internal by-slug 404 (see salon-service's SalonExceptionHandler.handleSalonNotFound).
    // Only a 404 carrying THIS marker is trusted to mean "salon-service looked, and there is
    // genuinely no salon for this slug".
    private static final URI GENUINE_SALON_NOT_FOUND_TYPE = URI.create("https://rivoo.com/errors/salon-not-found");

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
            if (isGenuineSalonNotFound(e)) {
                // salon-service legitimately has no salon for this slug. This must
                // surface as a domain "not found" — not as a 500 — so the two
                // anonymous public flows (availability, booking) can turn it into
                // the same response they give for a salon that exists but is not
                // ACTIVE, and an anonymous caller cannot enumerate slugs by
                // distinguishing a 500 (unknown slug) from a 422 (suspended salon).
                log.atWarn().addKeyValue("slug", slug).log("Salon not found in salon-service");
                throw new SalonNotFoundException(slug);
            }
            // A 404 WITHOUT the expected marker is not "the slug doesn't exist" — it can be a
            // misconfigured rivoo.services.salon-service.url, a renamed route, or a gateway
            // answering 404 for an unrelated reason. Treating every 404 as SalonNotFoundException
            // (as this method used to) would turn that misconfiguration into a silent, alert-free
            // outage: every public request would 404 as "no salon" instead of raising a signal
            // that pages someone. This falls through to a plain RuntimeException, exactly like an
            // upstream 5xx does below — same generic 500 body regardless of slug, so this does not
            // reopen the anti-enumeration oracle it would otherwise create a second way to probe.
            log.atError().setCause(e).addKeyValue("slug", slug)
                    .log("Received a 404 from salon-service without the expected 'salon-not-found' marker");
            throw new RuntimeException("Unexpected 404 from salon-service for slug: " + slug, e);
        } catch (HttpServerErrorException e) {
            log.atError().setCause(e).addKeyValue("slug", slug).log("salon-service responded with a server error");
            throw SalonServiceUnavailableException.serverError(
                    "salon-service returned a server error for slug: " + slug, e);
        } catch (ResourceAccessException e) {
            log.atError().setCause(e).addKeyValue("slug", slug).log("salon-service is unreachable");
            throw SalonServiceUnavailableException.unreachable(
                    "salon-service is unreachable for slug: " + slug, e);
        } catch (SalonNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("slug", slug).log("Failed to fetch salon by slug");
            throw new RuntimeException("Failed to fetch salon from salon-service: " + slug, e);
        }
    }

    private boolean isGenuineSalonNotFound(HttpClientErrorException.NotFound e) {
        try {
            ProblemDetail problem = e.getResponseBodyAs(ProblemDetail.class);
            return problem != null && GENUINE_SALON_NOT_FOUND_TYPE.equals(problem.getType());
        } catch (Exception parseError) {
            return false;
        }
    }
}
