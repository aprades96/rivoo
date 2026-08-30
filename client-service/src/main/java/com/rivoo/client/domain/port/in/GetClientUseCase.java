package com.rivoo.client.domain.port.in;

import com.rivoo.client.application.dto.ClientAppointmentsResponse;
import com.rivoo.client.application.dto.ClientResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetClientUseCase {

    ClientResponse getByExternalId(String externalId);

    Page<ClientResponse> list(String search, Pageable pageable);

    /**
     * Paginated appointment history for the client screen (D38). Unlike the GDPR export,
     * a failure here propagates — the endpoint has no fallback to an empty page.
     */
    ClientAppointmentsResponse getAppointmentHistory(String externalId, int page, int size);
}
