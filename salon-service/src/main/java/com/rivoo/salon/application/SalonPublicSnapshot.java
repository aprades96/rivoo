package com.rivoo.salon.application;

import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;

import java.util.List;

/**
 * Result of the transactional read step of the public salon aggregate:
 * the salon itself plus its business hours, both already fully materialized
 * (plain domain objects, not JPA proxies) so they remain safe to use once the
 * transaction that produced them has been committed and closed.
 */
record SalonPublicSnapshot(Salon salon, List<SalonBusinessHours> businessHours) {
}
