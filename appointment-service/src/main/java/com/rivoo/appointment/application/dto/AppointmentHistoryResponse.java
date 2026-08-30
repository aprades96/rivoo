package com.rivoo.appointment.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Paginated envelope for a client's appointment history, returned by the internal
 * endpoint that client-service delegates to (D38). {@code summary} is computed with
 * aggregate queries over the whole history, not just the current page.
 */
public record AppointmentHistoryResponse(
        List<AppointmentInternalResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Summary summary
) {

    /**
     * {@code totalAppointments} counts every status. {@code billedAmount} sums only
     * {@code COMPLETED} appointments — a cancelled or no-show visit was never charged.
     */
    public record Summary(
            long totalAppointments,
            BigDecimal billedAmount,
            long completedCount,
            Instant lastCompletedAt
    ) {
    }
}
