package com.rivoo.staff.infrastructure.adapter.out.persistence.repository;

import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.EmployeeWorkingHoursJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeWorkingHoursJpaRepository extends JpaRepository<EmployeeWorkingHoursJpaEntity, Long> {

    List<EmployeeWorkingHoursJpaEntity> findByEmployeeIdOrderByDayOfWeek(Long employeeId);

    void deleteByEmployeeId(Long employeeId);
}
