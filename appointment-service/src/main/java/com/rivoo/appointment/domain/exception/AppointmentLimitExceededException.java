package com.rivoo.appointment.domain.exception;

import com.rivoo.common.exception.PlanLimitExceededException;

/**
 * No {@code clientSafeDetail()} override, deliberately: the only throw site is
 * {@code AppointmentService#checkPlanLimits}, which both the authenticated
 * {@code POST /api/v1/appointments} and the ANONYMOUS {@code POST /api/v1/appointments/book}
 * reach — and a class-level override cannot distinguish them.
 * <p>
 * The message ("Monthly appointment limit of 200 reached") states the salon's plan ceiling, which
 * identifies its plan tier to anyone who can hit the public booking page. It now goes to the log
 * only. The 402 status and the "Plan Limit Exceeded" title are unchanged, so the salon owner's
 * own UI can still tell a plan problem from any other refusal.
 */
public class AppointmentLimitExceededException extends PlanLimitExceededException {
    public AppointmentLimitExceededException(int maxAppointments) {
        super("Monthly appointment limit of " + maxAppointments + " reached");
    }
}
