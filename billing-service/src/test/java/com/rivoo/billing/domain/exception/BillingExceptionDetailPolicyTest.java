package com.rivoo.billing.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Forces a DELIBERATE decision on every {@link RivooException} subtype in scope here.
 * <p>
 * The default of {@link RivooException#clientSafeDetail()} is {@code null} (publish nothing), so a
 * new subtype is safe by construction - but "safe" is not automatically "right": a subtype thrown
 * only behind {@code hasRole('SALON_OWNER')} SHOULD publish its message, and silently inheriting
 * the default would degrade the salon owner's error messages with nobody noticing. This test fails
 * in every direction: adding a subtype, removing one, adding or deleting an override, or gutting
 * an existing override's body.
 * <p>
 * The check is BEHAVIOURAL, not structural: each subtype is really instantiated and its
 * {@code clientSafeDetail()} really invoked, then compared against its real {@code getMessage()}.
 * An earlier version only asked whether the method was DECLARED, which reported "publishes" for an
 * override whose body had been changed to {@code return null} - a mutation it therefore could not
 * detect. Instantiating also guarantees the message is non-blank, without which the comparison
 * would be vacuous.
 * <p>
 * billing-service gets this guard because {@code GET /api/v1/billing/plans} and
 * {@code POST /api/webhooks/stripe} are both {@code permitAll} at the gateway and in
 * {@code BillingSecurityConfig}. Note that {@code StripeStubAdapter#constructEvent}
 * currently ignores the signature header entirely, so the webhook is genuinely open today:
 * every {@code true} below rests on the throw site being unreachable from
 * {@code WebhookService}, NOT on signature verification.
 */
class BillingExceptionDetailPolicyTest {

    /**
     * name -> does this subtype publish {@code getMessage()} to the caller?
     * <p>
     * {@code false} means "reachable from an anonymous endpoint, so the message goes to the log
     * only"; {@code true} means "every throw site is authenticated". The justification for each
     * entry lives as javadoc on the exception itself - this map only pins the outcome.
     */
    private static final Map<String, Boolean> EXPECTED = new TreeMap<>(Map.of(
            "DuplicateSubscriptionException", true,
            "PlanNotFoundException", true,
            "StripeCustomerNotLinkedException", true,
            "SubscriptionNotFoundException", true));

    @Test
    void everySubtypeDeclaresItsDetailPolicyExplicitly() {
        assertThat(scanSubtypes())
                .as("a new RivooException subtype must state whether its message is client-safe; "
                        + "see RivooException#clientSafeDetail")
                .isEqualTo(EXPECTED);
    }

    private static Map<String, Boolean> scanSubtypes() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(RivooException.class));

        Map<String, Boolean> found = new TreeMap<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.rivoo.billing")) {
            Class<?> type = load(definition.getBeanClassName());
            found.put(type.getSimpleName(), publishesItsMessage(type));
        }
        return found;
    }

    private static boolean publishesItsMessage(Class<?> type) {
        RivooException instance = instantiate(type);
        assertThat(instance.getMessage())
                .as("%s must build a non-blank message, otherwise comparing it to "
                        + "clientSafeDetail() proves nothing", type.getSimpleName())
                .isNotBlank();
        return instance.getMessage().equals(instance.clientSafeDetail());
    }

    /**
     * Builds a real instance through the type's own public API - a public constructor, or (for the
     * types that hide their constructor behind named factories) a public static factory - with
     * synthesized arguments. Nothing here bypasses the constructor, so the message under test is
     * the one production code would produce.
     */
    private static RivooException instantiate(Class<?> type) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!Modifier.isPublic(constructor.getModifiers())) {
                continue;
            }
            Object[] arguments = synthesize(constructor.getParameterTypes());
            if (arguments == null) {
                continue;
            }
            try {
                return (RivooException) constructor.newInstance(arguments);
            } catch (ReflectiveOperationException ignored) {
                // try the next candidate
            }
        }
        for (Method factory : type.getDeclaredMethods()) {
            if (!Modifier.isPublic(factory.getModifiers()) || !Modifier.isStatic(factory.getModifiers())
                    || !type.isAssignableFrom(factory.getReturnType())) {
                continue;
            }
            Object[] arguments = synthesize(factory.getParameterTypes());
            if (arguments == null) {
                continue;
            }
            try {
                return (RivooException) factory.invoke(null, arguments);
            } catch (ReflectiveOperationException ignored) {
                // try the next candidate
            }
        }
        throw new IllegalStateException("cannot instantiate " + type.getName()
                + " - add its parameter type to synthesize() rather than dropping it from this test");
    }

    private static Object[] synthesize(Class<?>[] parameterTypes) {
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == String.class) {
                arguments[i] = "sample-identifier";
            } else if (parameterType == int.class) {
                arguments[i] = 7;
            } else if (parameterType == long.class) {
                arguments[i] = 7L;
            } else if (Throwable.class.isAssignableFrom(parameterType)) {
                arguments[i] = new IllegalStateException("synthesized cause");
            } else {
                return null;
            }
        }
        return arguments;
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("scanned class is not loadable: " + className, e);
        }
    }
}
