package com.rivoo.appointment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AppointmentStatus state machine")
class AppointmentStatusTest {

    // -------------------------------------------------------------------------
    // isTerminal()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("isTerminal()")
    class IsTerminal {

        @Test
        @DisplayName("COMPLETED is terminal")
        void completedIsTerminal() {
            assertTrue(AppointmentStatus.COMPLETED.isTerminal());
        }

        @Test
        @DisplayName("CANCELLED is terminal")
        void cancelledIsTerminal() {
            assertTrue(AppointmentStatus.CANCELLED.isTerminal());
        }

        @Test
        @DisplayName("NO_SHOW is terminal")
        void noShowIsTerminal() {
            assertTrue(AppointmentStatus.NO_SHOW.isTerminal());
        }

        @Test
        @DisplayName("PENDING is not terminal")
        void pendingIsNotTerminal() {
            assertFalse(AppointmentStatus.PENDING.isTerminal());
        }

        @Test
        @DisplayName("CONFIRMED is not terminal")
        void confirmedIsNotTerminal() {
            assertFalse(AppointmentStatus.CONFIRMED.isTerminal());
        }

        @Test
        @DisplayName("IN_PROGRESS is not terminal")
        void inProgressIsNotTerminal() {
            assertFalse(AppointmentStatus.IN_PROGRESS.isTerminal());
        }
    }

    // -------------------------------------------------------------------------
    // canTransitionTo() — PENDING
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("From PENDING")
    class FromPending {

        @Test
        @DisplayName("PENDING -> CONFIRMED is valid")
        void pendingToConfirmed() {
            assertTrue(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.CONFIRMED));
        }

        @Test
        @DisplayName("PENDING -> CANCELLED is valid")
        void pendingToCancelled() {
            assertTrue(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.CANCELLED));
        }

        @Test
        @DisplayName("PENDING -> COMPLETED is invalid")
        void pendingToCompleted() {
            assertFalse(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.COMPLETED));
        }

        @Test
        @DisplayName("PENDING -> IN_PROGRESS is invalid")
        void pendingToInProgress() {
            assertFalse(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.IN_PROGRESS));
        }

        @Test
        @DisplayName("PENDING -> NO_SHOW is valid")
        void pendingToNoShow() {
            assertTrue(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.NO_SHOW));
        }
    }

    // -------------------------------------------------------------------------
    // canTransitionTo() — CONFIRMED
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("From CONFIRMED")
    class FromConfirmed {

        @Test
        @DisplayName("CONFIRMED -> IN_PROGRESS is valid")
        void confirmedToInProgress() {
            assertTrue(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.IN_PROGRESS));
        }

        @Test
        @DisplayName("CONFIRMED -> CANCELLED is valid")
        void confirmedToCancelled() {
            assertTrue(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.CANCELLED));
        }

        @Test
        @DisplayName("CONFIRMED -> NO_SHOW is valid")
        void confirmedToNoShow() {
            assertTrue(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.NO_SHOW));
        }

        @Test
        @DisplayName("CONFIRMED -> COMPLETED is invalid (must go through IN_PROGRESS)")
        void confirmedToCompleted() {
            assertFalse(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.COMPLETED));
        }

        @Test
        @DisplayName("CONFIRMED -> PENDING is invalid")
        void confirmedToPending() {
            assertFalse(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.PENDING));
        }
    }

    // -------------------------------------------------------------------------
    // canTransitionTo() — IN_PROGRESS
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("From IN_PROGRESS")
    class FromInProgress {

        @Test
        @DisplayName("IN_PROGRESS -> COMPLETED is valid")
        void inProgressToCompleted() {
            assertTrue(AppointmentStatus.IN_PROGRESS.canTransitionTo(AppointmentStatus.COMPLETED));
        }

        @Test
        @DisplayName("IN_PROGRESS -> CONFIRMED is invalid")
        void inProgressToConfirmed() {
            assertFalse(AppointmentStatus.IN_PROGRESS.canTransitionTo(AppointmentStatus.CONFIRMED));
        }

        @Test
        @DisplayName("IN_PROGRESS -> CANCELLED is invalid")
        void inProgressToCancelled() {
            assertFalse(AppointmentStatus.IN_PROGRESS.canTransitionTo(AppointmentStatus.CANCELLED));
        }

        @Test
        @DisplayName("IN_PROGRESS -> NO_SHOW is invalid")
        void inProgressToNoShow() {
            assertFalse(AppointmentStatus.IN_PROGRESS.canTransitionTo(AppointmentStatus.NO_SHOW));
        }
    }

    // -------------------------------------------------------------------------
    // canTransitionTo() — terminal states
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("From terminal states (COMPLETED, CANCELLED, NO_SHOW)")
    class FromTerminal {

        @Test
        @DisplayName("COMPLETED -> PENDING is invalid")
        void completedToPending() {
            assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(AppointmentStatus.PENDING));
        }

        @Test
        @DisplayName("COMPLETED -> CONFIRMED is invalid")
        void completedToConfirmed() {
            assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(AppointmentStatus.CONFIRMED));
        }

        @Test
        @DisplayName("COMPLETED -> IN_PROGRESS is invalid")
        void completedToInProgress() {
            assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(AppointmentStatus.IN_PROGRESS));
        }

        @Test
        @DisplayName("COMPLETED -> CANCELLED is invalid")
        void completedToCancelled() {
            assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(AppointmentStatus.CANCELLED));
        }

        @Test
        @DisplayName("COMPLETED -> NO_SHOW is invalid")
        void completedToNoShow() {
            assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(AppointmentStatus.NO_SHOW));
        }

        @Test
        @DisplayName("CANCELLED -> PENDING is invalid")
        void cancelledToPending() {
            assertFalse(AppointmentStatus.CANCELLED.canTransitionTo(AppointmentStatus.PENDING));
        }

        @Test
        @DisplayName("CANCELLED -> CONFIRMED is invalid")
        void cancelledToConfirmed() {
            assertFalse(AppointmentStatus.CANCELLED.canTransitionTo(AppointmentStatus.CONFIRMED));
        }

        @Test
        @DisplayName("CANCELLED -> IN_PROGRESS is invalid")
        void cancelledToInProgress() {
            assertFalse(AppointmentStatus.CANCELLED.canTransitionTo(AppointmentStatus.IN_PROGRESS));
        }

        @Test
        @DisplayName("CANCELLED -> COMPLETED is invalid")
        void cancelledToCompleted() {
            assertFalse(AppointmentStatus.CANCELLED.canTransitionTo(AppointmentStatus.COMPLETED));
        }

        @Test
        @DisplayName("CANCELLED -> NO_SHOW is invalid")
        void cancelledToNoShow() {
            assertFalse(AppointmentStatus.CANCELLED.canTransitionTo(AppointmentStatus.NO_SHOW));
        }

        @Test
        @DisplayName("NO_SHOW -> PENDING is invalid")
        void noShowToPending() {
            assertFalse(AppointmentStatus.NO_SHOW.canTransitionTo(AppointmentStatus.PENDING));
        }

        @Test
        @DisplayName("NO_SHOW -> CONFIRMED is invalid")
        void noShowToConfirmed() {
            assertFalse(AppointmentStatus.NO_SHOW.canTransitionTo(AppointmentStatus.CONFIRMED));
        }

        @Test
        @DisplayName("NO_SHOW -> COMPLETED is invalid")
        void noShowToCompleted() {
            assertFalse(AppointmentStatus.NO_SHOW.canTransitionTo(AppointmentStatus.COMPLETED));
        }

        @Test
        @DisplayName("NO_SHOW -> CANCELLED is invalid")
        void noShowToCancelled() {
            assertFalse(AppointmentStatus.NO_SHOW.canTransitionTo(AppointmentStatus.CANCELLED));
        }
    }
}
