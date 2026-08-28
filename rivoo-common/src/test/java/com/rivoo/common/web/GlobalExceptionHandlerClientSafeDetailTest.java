package com.rivoo.common.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rivoo.common.exception.RivooException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The contract of {@link RivooException#clientSafeDetail()} as {@link GlobalExceptionHandler}
 * applies it, exercised end to end through a real controller with the real advice registered.
 * <p>
 * The three exception types below are declared here rather than reusing production ones on
 * purpose: this test pins the MECHANISM (what the handler does with an override, without one, and
 * with a 5xx), independently of any particular subtype's current decision. The per-subtype
 * decisions are pinned by the policy tests in each service module.
 */
class GlobalExceptionHandlerClientSafeDetailTest {

    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        logAppender = new ListAppender<>();
        logAppender.start();
        handlerLogger().addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        handlerLogger().detachAppender(logAppender);
    }

    private static Logger handlerLogger() {
        return (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    }

    @Test
    void subtypeWithoutOverride_publishesTheGenericDetailAndNeverTheMessage() throws Exception {
        mockMvc.perform(get("/silent"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.GENERIC_DETAIL))
                .andExpect(jsonPath("$.title").value("Silent"))
                .andExpect(jsonPath("$.type").value("https://rivoo.com/errors/silent"));

        assertLogged(Level.WARN, "employee 'Ana Garcia' at 10:00", SilentException.class);
    }

    @Test
    void subtypeOverridingClientSafeDetail_stillPublishesItsMessage() throws Exception {
        mockMvc.perform(get("/talkative"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("useful for the salon owner"));

        assertLogged(Level.WARN, "useful for the salon owner", TalkativeException.class);
    }

    /**
     * A 5xx is an operator problem, not a caller problem, so it must land at ERROR — the level
     * that pages someone — while a 4xx stays at WARN. Both keep the message and the cause.
     */
    @Test
    void serverErrorSubtype_logsAtErrorAndStillHidesItsMessage() throws Exception {
        mockMvc.perform(get("/broken"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.GENERIC_DETAIL));

        assertLogged(Level.ERROR, "billing-internal.rivoo.local:8087 refused the connection",
                ServerSideException.class);
    }

    private void assertLogged(Level expectedLevel, String expectedMessageFragment, Class<?> expectedType) {
        assertThat(logAppender.list)
                .as("the message must reach the log whatever the response publishes")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(expectedLevel);
                    assertThat(event.getFormattedMessage()).isEqualTo("Rivoo exception");
                    assertThat(event.getKeyValuePairs())
                            .anySatisfy(pair -> {
                                assertThat(pair.key).isEqualTo("internalDetail");
                                assertThat(String.valueOf(pair.value)).contains(expectedMessageFragment);
                            });
                    assertThat(event.getThrowableProxy())
                            .as("setCause(ex) must attach the exception so the stack trace survives")
                            .isNotNull();
                    assertThat(event.getThrowableProxy().getClassName()).isEqualTo(expectedType.getName());
                });
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/silent")
        String silent() {
            throw new SilentException();
        }

        @GetMapping("/talkative")
        String talkative() {
            throw new TalkativeException();
        }

        @GetMapping("/broken")
        String broken() {
            throw new ServerSideException();
        }
    }

    /** A subtype that made no decision — the case that used to leak by default. */
    static class SilentException extends RivooException {
        SilentException() {
            super("conflict with employee 'Ana Garcia' at 10:00", "silent", "Silent",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** A subtype whose every throw site is authenticated, so it opts in to publishing. */
    static class TalkativeException extends RivooException {
        TalkativeException() {
            super("useful for the salon owner", "talkative", "Talkative", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Override
        public String clientSafeDetail() {
            return getMessage();
        }
    }

    static class ServerSideException extends RivooException {
        ServerSideException() {
            super("billing-internal.rivoo.local:8087 refused the connection", "broken", "Broken",
                    HttpStatus.BAD_GATEWAY);
        }
    }
}
