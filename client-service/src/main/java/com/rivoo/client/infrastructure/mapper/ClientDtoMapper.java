package com.rivoo.client.infrastructure.mapper;

import com.rivoo.client.application.dto.ClientExportResponse;
import com.rivoo.client.application.dto.ClientInternalResponse;
import com.rivoo.client.application.dto.ClientResponse;
import com.rivoo.client.domain.model.Client;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ClientDtoMapper {

    @Mapping(target = "id", source = "client.externalId")
    @Mapping(target = "gender", expression = "java(client.getGender() != null ? client.getGender().name() : null)")
    @Mapping(target = "source", expression = "java(client.getSource() != null ? client.getSource().name() : null)")
    ClientResponse toResponse(Client client);

    @Mapping(target = "id", source = "client.externalId")
    @Mapping(target = "gender", expression = "java(client.getGender() != null ? client.getGender().name() : null)")
    @Mapping(target = "source", expression = "java(client.getSource() != null ? client.getSource().name() : null)")
    @Mapping(target = "appointments", source = "appointments")
    ClientExportResponse toExportResponse(Client client, List<String> appointments);

    @Mapping(target = "id", source = "externalId")
    ClientInternalResponse toInternalResponse(Client client);
}
