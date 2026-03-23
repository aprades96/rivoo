package com.rivoo.auth.application;

import com.rivoo.auth.application.dto.*;
import com.rivoo.auth.domain.model.EventType;
import com.rivoo.auth.domain.model.OnboardingEvent;
import com.rivoo.auth.domain.model.TenantUserMapping;
import com.rivoo.auth.domain.model.UserRole;
import com.rivoo.auth.domain.port.in.*;
import com.rivoo.auth.domain.port.out.KeycloakAdminPort;
import com.rivoo.auth.domain.port.out.OnboardingEventPort;
import com.rivoo.auth.domain.port.out.TenantUserMappingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class AuthService implements RegisterOwnerUseCase, RegisterEmployeeUseCase,
        ManageTenantStatusUseCase, UpdateTenantAttributeUseCase, ListTenantUsersUseCase {

    private final KeycloakAdminPort keycloakAdminPort;
    private final OnboardingEventPort onboardingEventPort;
    private final TenantUserMappingPort tenantUserMappingPort;

    @Override
    @Transactional
    public RegisterOwnerResponse registerOwner(RegisterOwnerRequest request) {
        log.atInfo().addKeyValue("email", request.email()).log("Registering owner");

        String keycloakUserId = keycloakAdminPort.createUser(
                request.email(), request.password(), request.firstName(), request.lastName());

        try {
            String plan = request.subscriptionPlan() != null ? request.subscriptionPlan() : "FREE_TRIAL";
            keycloakAdminPort.setUserAttributes(keycloakUserId, Map.of(
                    "tenant_id", List.of(request.tenantId()),
                    "subscription_plan", List.of(plan),
                    "salon_name", List.of(request.salonName())
            ));

            keycloakAdminPort.assignRealmRole(keycloakUserId, UserRole.SALON_OWNER.toKeycloakRole());

            tenantUserMappingPort.save(new TenantUserMapping(
                    request.tenantId(), keycloakUserId, UserRole.SALON_OWNER));

            onboardingEventPort.save(new OnboardingEvent(
                    request.tenantId(), keycloakUserId, request.email(),
                    EventType.OWNER_CREATED, null));

            log.atInfo().addKeyValue("keycloakUserId", keycloakUserId).log("Owner registered successfully");

            return new RegisterOwnerResponse(keycloakUserId, request.email(), UserRole.SALON_OWNER.name());

        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("keycloakUserId", keycloakUserId).log("Owner registration failed, compensating");
            compensateUserCreation(keycloakUserId);
            throw e;
        }
    }

    @Override
    @Transactional
    public RegisterEmployeeResponse registerEmployee(RegisterEmployeeRequest request) {
        log.atInfo().addKeyValue("email", request.email()).log("Registering employee");

        String keycloakUserId = keycloakAdminPort.createEmployeeUser(
                request.email(), request.password(), request.firstName(), request.lastName());

        try {
            keycloakAdminPort.setUserAttributes(keycloakUserId, Map.of(
                    "tenant_id", List.of(request.tenantId())
            ));

            keycloakAdminPort.assignRealmRole(keycloakUserId, UserRole.EMPLOYEE.toKeycloakRole());

            tenantUserMappingPort.save(new TenantUserMapping(
                    request.tenantId(), keycloakUserId, UserRole.EMPLOYEE));

            onboardingEventPort.save(new OnboardingEvent(
                    request.tenantId(), keycloakUserId, request.email(),
                    EventType.EMPLOYEE_CREATED, null));

            // Send email with link to set password (fire-and-forget)
            try {
                keycloakAdminPort.sendRequiredActionsEmail(keycloakUserId);
            } catch (Exception emailError) {
                log.atWarn().setCause(emailError).addKeyValue("keycloakUserId", keycloakUserId)
                        .log("Failed to send required actions email, employee can still use temp password");
            }

            log.atInfo().addKeyValue("keycloakUserId", keycloakUserId).log("Employee registered successfully");

            return new RegisterEmployeeResponse(keycloakUserId, request.email(), UserRole.EMPLOYEE.name());

        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("keycloakUserId", keycloakUserId).log("Employee registration failed, compensating");
            compensateUserCreation(keycloakUserId);
            throw e;
        }
    }

    @Override
    @Transactional
    public void disableTenant(String tenantId) {
        log.atInfo().log("Disabling all users for tenant");
        setTenantStatus(tenantId, false);
    }

    @Override
    @Transactional
    public void setTenantStatus(String tenantId, boolean enabled) {
        log.atInfo().addKeyValue("enabled", enabled).log("Setting tenant status");

        List<String> userIds = keycloakAdminPort.searchUserIdsByAttribute("tenant_id", tenantId);

        for (String userId : userIds) {
            keycloakAdminPort.setUserEnabled(userId, enabled);
        }

        tenantUserMappingPort.updateActiveStatusByTenantId(tenantId, enabled);

        EventType eventType = enabled ? EventType.USER_ENABLED : EventType.USER_DISABLED;
        for (String userId : userIds) {
            onboardingEventPort.save(new OnboardingEvent(
                    tenantId, userId, "",
                    eventType, "{\"usersAffected\":%d}".formatted(userIds.size())));
        }

        log.atInfo().addKeyValue("enabled", enabled).addKeyValue("usersAffected", userIds.size()).log("Tenant status updated");
    }

    @Override
    public void updateTenantAttributes(String tenantId, UpdateAttributeRequest request) {
        log.atInfo().addKeyValue("attributeKeys", request.attributes().keySet()).log("Updating tenant attributes");

        List<String> userIds = keycloakAdminPort.searchUserIdsByAttribute("tenant_id", tenantId);

        for (String userId : userIds) {
            for (var entry : request.attributes().entrySet()) {
                keycloakAdminPort.updateUserAttribute(userId, entry.getKey(), entry.getValue());
            }
        }

        log.atInfo().addKeyValue("usersAffected", userIds.size()).log("Tenant attributes updated");
    }

    @Override
    public List<TenantUserResponse> listTenantUsers(String tenantId) {
        log.atDebug().log("Listing users for tenant");

        return tenantUserMappingPort.findByTenantId(tenantId).stream()
                .map(mapping -> new TenantUserResponse(
                        mapping.getKeycloakUserId(),
                        mapping.getTenantId(),
                        mapping.getRole().name(),
                        mapping.isActive()))
                .toList();
    }

    private void compensateUserCreation(String keycloakUserId) {
        try {
            keycloakAdminPort.deleteUser(keycloakUserId);
            log.atInfo().addKeyValue("keycloakUserId", keycloakUserId).log("Compensation: deleted Keycloak user");
        } catch (Exception compensationError) {
            log.atError().setCause(compensationError).addKeyValue("keycloakUserId", keycloakUserId).log("Compensation FAILED: could not delete Keycloak user, manual cleanup required");
        }
    }
}
