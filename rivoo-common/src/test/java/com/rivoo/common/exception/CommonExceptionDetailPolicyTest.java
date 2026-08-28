package com.rivoo.common.exception;

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
 * These are the SHARED BASE CLASSES, and for them a blanket override is not an option: it would be
 * inherited by every subtype in every service, present and future, whatever endpoint throws it.
 * That is exactly the fail-open default that let {@code AppointmentConflictException} - a
 * {@link BusinessValidationException} - hand an employee's full name to unauthenticated callers.
 * So every entry below stays {@code false}: a subtype opts in for itself, or a single throw site
 * does through a factory, but never the base class for all of them.
 * <p>
 * The map therefore pins DEFAULT CONSTRUCTION only, and that is a real limitation of it:
 * {@link #instantiate} uses the first usable public constructor, so a per-site opt-in factory such
 * as {@link BusinessValidationException#clientSafe(String)} is invisible here and the pinned value
 * stays {@code false} either way. {@link #businessValidationExceptionPublishesOnlyWhenAThrowSiteOptsIn()}
 * covers the other half - add the same kind of test alongside any future factory, otherwise this
 * map silently stops describing what the type can do.
 */
class CommonExceptionDetailPolicyTest {

    /**
     * name -> does an instance built through the type's plain public constructor publish
     * {@code getMessage()} to the caller?
     * <p>
     * {@code false} means "publishes nothing by default, so the message goes to the log only";
     * {@code true} means "every throw site is authenticated and the message is always published".
     * For a base class with mixed reachability - {@link BusinessValidationException} is thrown
     * both from the anonymous {@code POST /api/v1/appointments/book} and from endpoints behind
     * {@code hasRole('SALON_OWNER')} - {@code false} is the only correct entry, and the
     * authenticated sites opt in one by one. The justification for each entry lives as javadoc on
     * the exception itself; this map only pins the outcome.
     */
    private static final Map<String, Boolean> EXPECTED = new TreeMap<>(Map.of(
            "BusinessValidationException", false,
            "PlanLimitExceededException", false,
            "ResourceNotFoundException", false,
            "TenantMismatchException", false));

    /**
     * The half {@link #EXPECTED} structurally cannot see. Both directions are asserted from the
     * same message, so a mutation that makes the factory return {@code null}, or that makes the
     * plain constructor publish, fails here even though the map above would stay green.
     */
    @Test
    void businessValidationExceptionPublishesOnlyWhenAThrowSiteOptsIn() {
        String message = "closeTime must be after openTime";

        BusinessValidationException restrictiveByDefault = new BusinessValidationException(message);
        assertThat(restrictiveByDefault.clientSafeDetail())
                .as("the plain constructor is what every subtype reaches through super(message); "
                        + "it must publish nothing, or the whole hierarchy fails open again")
                .isNull();

        BusinessValidationException optedIn = BusinessValidationException.clientSafe(message);
        assertThat(optedIn.clientSafeDetail())
                .as("clientSafe() exists to publish the message of an authenticated throw site")
                .isEqualTo(message)
                .isEqualTo(optedIn.getMessage());

        assertThat(optedIn)
                .as("opting in must change what is published and nothing else - same status, "
                        + "same RFC 9457 type and title as any other business validation failure")
                .extracting(RivooException::getHttpStatus, RivooException::getErrorType, RivooException::getErrorTitle)
                .containsExactly(restrictiveByDefault.getHttpStatus(),
                        restrictiveByDefault.getErrorType(),
                        restrictiveByDefault.getErrorTitle());
    }

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

        // Scoped to the exception package, not the whole com.rivoo.common tree: the scanner
        // sees target/test-classes too, and GlobalExceptionHandlerClientSafeDetailTest
        // deliberately declares throwaway RivooException subtypes (one of which DOES
        // override) to exercise the handler. Those are fixtures, not shared base classes.
        Map<String, Boolean> found = new TreeMap<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.rivoo.common.exception")) {
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
