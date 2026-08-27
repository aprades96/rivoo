package com.rivoo.staff.infrastructure.mapper;

import com.rivoo.staff.application.dto.ServiceOfferingInternalResponse;
import com.rivoo.staff.application.dto.ServiceOfferingPublicResponse;
import com.rivoo.staff.application.dto.ServiceOfferingResponse;
import com.rivoo.staff.domain.model.ServiceOffering;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ServiceOfferingDtoMapper {

    @Mapping(target = "id", source = "externalId")
    ServiceOfferingResponse toResponse(ServiceOffering service);

    @Mapping(target = "id", source = "externalId")
    ServiceOfferingInternalResponse toInternalResponse(ServiceOffering service);

    @Mapping(target = "id", source = "externalId")
    ServiceOfferingPublicResponse toPublicResponse(ServiceOffering service);
}
