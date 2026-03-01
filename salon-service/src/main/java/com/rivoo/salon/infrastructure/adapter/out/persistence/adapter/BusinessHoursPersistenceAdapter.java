package com.rivoo.salon.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.infrastructure.adapter.out.persistence.entity.SalonBusinessHoursJpaEntity;
import com.rivoo.salon.infrastructure.adapter.out.persistence.repository.SalonBusinessHoursJpaRepository;
import com.rivoo.salon.infrastructure.mapper.SalonPersistenceMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BusinessHoursPersistenceAdapter implements BusinessHoursPersistencePort {

    private final SalonBusinessHoursJpaRepository repository;
    private final SalonPersistenceMapper mapper;
    private final EntityManager entityManager;

    @Override
    public List<SalonBusinessHours> findBySalonId(Long salonId) {
        return repository.findBySalonIdOrderByDayOfWeek(salonId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<SalonBusinessHours> saveAll(List<SalonBusinessHours> hours) {
        List<SalonBusinessHoursJpaEntity> entities = hours.stream()
                .map(mapper::toJpaEntity).toList();
        return repository.saveAll(entities).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteBySalonId(Long salonId) {
        repository.deleteBySalonId(salonId);
        // Flush required: within the same transaction, delete must be visible
        // before saveAll to avoid unique constraint violation on (salon_id, day_of_week)
        entityManager.flush();
    }
}
