package com.rivoo.client.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class ClientNotFoundException extends ResourceNotFoundException {

    public ClientNotFoundException(String identifier) {
        super("client", identifier);
    }
}
