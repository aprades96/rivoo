package com.rivoo.common.tenant;

import com.rivoo.common.observability.ObservabilityAutoConfiguration;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration(after = {ObservabilityAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
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
    @ConditionalOnClass(name = "jakarta.persistence.EntityManagerFactory")
    public TenantFilterAspect tenantFilterAspect(EntityManager entityManager) {
        return new TenantFilterAspect(entityManager);
    }
}
