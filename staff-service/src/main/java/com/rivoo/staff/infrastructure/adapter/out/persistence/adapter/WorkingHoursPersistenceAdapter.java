package com.rivoo.staff.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.staff.domain.model.EmployeeWorkingHours;
import com.rivoo.staff.domain.port.out.WorkingHoursPersistencePort;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.EmployeeWorkingHoursJpaEntity;
import com.rivoo.staff.infrastructure.adapter.out.persistence.repository.EmployeeWorkingHoursJpaRepository;
import com.rivoo.staff.infrastructure.mapper.EmployeePersistenceMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkingHoursPersistenceAdapter implements WorkingHoursPersistencePort {

    private final EmployeeWorkingHoursJpaRepository repository;
    private final EmployeePersistenceMapper mapper;
    private final EntityManager entityManager;

    @Override
    public List<EmployeeWorkingHours> findByEmployeeId(Long employeeId) {
        return repository.findByEmployeeIdOrderByDayOfWeek(employeeId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<EmployeeWorkingHours> saveAll(List<EmployeeWorkingHours> hours) {
        List<EmployeeWorkingHoursJpaEntity> entities = hours.stream()
                .map(mapper::toJpaEntity).toList();
        return repository.saveAll(entities).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteByEmployeeId(Long employeeId) {
        repository.deleteByEmployeeId(employeeId);
        entityManager.flush();
    }
}
