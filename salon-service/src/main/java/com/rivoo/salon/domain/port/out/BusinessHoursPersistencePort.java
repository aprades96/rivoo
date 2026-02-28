package com.rivoo.salon.domain.port.out;

import com.rivoo.salon.domain.model.SalonBusinessHours;

import java.util.List;

public interface BusinessHoursPersistencePort {

    List<SalonBusinessHours> findBySalonId(Long salonId);

    List<SalonBusinessHours> saveAll(List<SalonBusinessHours> hours);

    void deleteBySalonId(Long salonId);
}
