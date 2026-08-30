package com.rivoo.client.infrastructure.mapper;

import com.rivoo.client.application.dto.ClientResponse;
import com.rivoo.client.domain.model.Client;
import com.rivoo.client.domain.model.ClientSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks D37: MapStruct silently drops a target field when its name has no matching source
 * property, with no compile-time warning. {@link ClientResponseJsonTest} only proves the record
 * component serializes to the right JSON key; this test proves the generated
 * {@code ClientDtoMapperImpl} actually reads {@code Client.gdprConsentAt} into it.
 */
class ClientDtoMapperTest {

    private final ClientDtoMapper mapper = new ClientDtoMapperImpl();

    @Test
    void toResponse_populatesGdprConsentAtFromDomainModel() {
        Instant consentAt = Instant.parse("2026-01-15T09:30:00Z");
        Client client = Client.builder()
                .externalId("cli_abc123")
                .firstName("Ana")
                .lastName("Lopez")
                .source(ClientSource.WALK_IN)
                .gdprConsentAt(consentAt)
                .active(true)
                .build();

        ClientResponse response = mapper.toResponse(client);

        assertThat(response.gdprConsentAt()).isEqualTo(consentAt);
    }
}
