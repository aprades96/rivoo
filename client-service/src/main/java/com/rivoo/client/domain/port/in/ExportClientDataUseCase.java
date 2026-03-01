package com.rivoo.client.domain.port.in;

import com.rivoo.client.application.dto.ClientExportResponse;

public interface ExportClientDataUseCase {

    ClientExportResponse export(String externalId);
}
