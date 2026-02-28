package com.rivoo.salon.domain.port.out;

import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SalonPersistencePort {

    Salon save(Salon salon);

    Optional<Salon> findByTenantId(String tenantId);

    Optional<Salon> findBySlug(String slug);

    boolean existsBySlug(String slug);

    void deleteById(Long id);

    Page<Salon> findAll(Pageable pageable);

    List<Salon> findByStatusAndCreatedAtBefore(SalonStatus status, Instant before);
}
