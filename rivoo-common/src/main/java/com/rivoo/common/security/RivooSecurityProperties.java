package com.rivoo.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rivoo.security")
public record RivooSecurityProperties(String internalServiceKey) {

    public RivooSecurityProperties {
        if (internalServiceKey == null) internalServiceKey = "";
    }
}
