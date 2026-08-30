package com.rivoo.client.domain.port.out;

import com.rivoo.client.application.dto.ClientAppointmentDto;
import com.rivoo.client.application.dto.ClientAppointmentsResponse;

import java.util.List;

public interface AppointmentServicePort {

    /**
     * Used only by the GDPR export (ClientService.export). Degrades to an empty list
     * on failure — that behaviour is intentional and must not change (D38, "salvo que").
     */
    List<ClientAppointmentDto> getClientAppointments(String clientExternalId, String tenantId);

    /**
     * Paginated history for the client screen (D38). Unlike {@link #getClientAppointments},
     * this one does NOT swallow failures: the caller must see a real error, not an empty page
     * indistinguishable from "no appointments".
     */
    ClientAppointmentsResponse getClientAppointmentsPage(String clientExternalId, String tenantId, int page, int size);
}
