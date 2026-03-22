package com.rivoo.appointment.domain.port.out;

public interface SalonServicePort {

    SalonInfo getSalonBySlug(String slug);

    record SalonInfo(String tenantId, String name, String status) {
    }
}
