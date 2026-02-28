package com.rivoo.salon.infrastructure.adapter.out.persistence.repository;

import com.rivoo.salon.infrastructure.adapter.out.persistence.entity.SalonBusinessHoursJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalonBusinessHoursJpaRepository extends JpaRepository<SalonBusinessHoursJpaEntity, Long> {

    List<SalonBusinessHoursJpaEntity> findBySalonIdOrderByDayOfWeek(Long salonId);

    void deleteBySalonId(Long salonId);
}
