package com.rivoo.common.web;

import com.rivoo.common.security.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration(after = SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
public class WebAutoConfiguration {
}
