package com.rivoo.common.tenant;

import com.rivoo.common.observability.ObservabilityAutoConfiguration;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
public class TenantAutoConfiguration implements WebMvcConfigurer {

    @Bean
    public TenantInterceptor tenantInterceptor() {
        return new TenantInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor());
    }

    @Bean
    @ConditionalOnBean(EntityManager.class)
    public TenantFilterAspect tenantFilterAspect(EntityManager entityManager) {
        return new TenantFilterAspect(entityManager);
    }
}
