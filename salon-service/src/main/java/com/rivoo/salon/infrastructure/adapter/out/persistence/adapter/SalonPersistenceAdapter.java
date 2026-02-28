package com.rivoo.salon.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.infrastructure.adapter.out.persistence.entity.SalonJpaEntity;
import com.rivoo.salon.infrastructure.adapter.out.persistence.repository.SalonJpaRepository;
import com.rivoo.salon.infrastructure.mapper.SalonPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SalonPersistenceAdapter implements SalonPersistencePort {

    private final SalonJpaRepository salonJpaRepository;
    private final SalonPersistenceMapper mapper;

    @Override
    public Salon save(Salon salon) {
        SalonJpaEntity entity = mapper.toJpaEntity(salon);
        SalonJpaEntity saved = salonJpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Salon> findByTenantId(String tenantId) {
        return salonJpaRepository.findByTenantId(tenantId).map(mapper::toDomain);
    }

    @Override
    public Optional<Salon> findBySlug(String slug) {
        return salonJpaRepository.findBySlug(slug).map(mapper::toDomain);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return salonJpaRepository.existsBySlug(slug);
    }

    @Override
    public void deleteById(Long id) {
        salonJpaRepository.deleteById(id);
    }

    @Override
    public Page<Salon> findAll(Pageable pageable) {
        return salonJpaRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public List<Salon> findByStatusAndCreatedAtBefore(SalonStatus status, Instant before) {
        return salonJpaRepository.findByStatusAndCreatedAtBefore(status, before)
                .stream().map(mapper::toDomain).toList();
    }
}
