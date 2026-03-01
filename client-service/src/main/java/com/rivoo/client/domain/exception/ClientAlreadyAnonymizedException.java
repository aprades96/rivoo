package com.rivoo.client.domain.exception;

import com.rivoo.common.exception.BusinessValidationException;

public class ClientAlreadyAnonymizedException extends BusinessValidationException {

    public ClientAlreadyAnonymizedException(String identifier) {
        super("Client '" + identifier + "' has already been anonymized");
    }
}
