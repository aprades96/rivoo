package com.rivoo.client.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response of {@code GET /api/v1/clients/{id}/appointments} (D38). Wraps a page of
 * {@link ClientAppointmentDto} plus a summary computed with aggregate queries in
 * appointment-service over the whole history, not just the current page.
 */
public record ClientAppointmentsResponse(
        List<ClientAppointmentDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Summary summary
) {

    /**
     * {@code totalAppointments} counts every status (matches the "14 citas" headline).
     * {@code billedAmount} sums only {@code COMPLETED} appointments — "facturados" means
     * charged, and a cancelled or no-show visit was never charged. The two figures have
     * different denominators on purpose; do not derive an average ticket from them.
     */
    public record Summary(
            long totalAppointments,
            BigDecimal billedAmount,
            long completedCount,
            Instant lastCompletedAt
    ) {
    }
}
