package com.rivoo.appointment.domain.exception;

import com.rivoo.common.exception.BusinessValidationException;

/**
 * No {@code clientSafeDetail()} override, deliberately: one of the two throw sites is
 * {@code AppointmentService#book}, i.e. the ANONYMOUS {@code POST /api/v1/appointments/book}.
 * <p>
 * The message names an employee ("Employee 'Ana Garcia' already has an appointment during
 * 10:00-11:00"), so publishing it handed an unauthenticated caller a staff member's full name
 * plus a booked slot for any salon whose public slug they knew. That message is now a
 * server-side diagnostic only: {@code GlobalExceptionHandler} logs it with the exception as
 * cause and publishes the generic detail instead. The caller still gets 422 and the
 * "Business Validation Failed" title, which is enough for the booking form to retry a slot.
 */
public class AppointmentConflictException extends BusinessValidationException {
    public AppointmentConflictException(String employeeName, String timeRange) {
        super("Employee '" + employeeName + "' already has an appointment during " + timeRange);
    }
}
