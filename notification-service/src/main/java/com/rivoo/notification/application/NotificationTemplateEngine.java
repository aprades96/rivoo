package com.rivoo.notification.application;

import com.rivoo.notification.domain.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationTemplateEngine {

    public record TemplateResult(String subject, String body) {}

    public TemplateResult render(NotificationType type, Map<String, String> data) {
        Map<String, String> safeData = data != null ? data : Map.of();
        return switch (type) {
            case WELCOME -> new TemplateResult(
                    "Bienvenido a Rivoo",
                    "Hola " + safeData.getOrDefault("salonName", "") + ", tu salón está activo en Rivoo."
            );
            case REGISTRATION_ATTEMPT_EXISTING_ACCOUNT -> new TemplateResult(
                    "Intento de registro en Rivoo",
                    "Alguien ha intentado crear una cuenta en Rivoo con esta direccion de correo. " +
                    "Ya existe una cuenta asociada a ella, asi que no hemos creado ninguna nueva. " +
                    "Si has sido tu, inicia sesion con tus datos habituales; si no recuerdas la " +
                    "contraseña, usa la opcion de recuperarla desde la pantalla de inicio de sesion. " +
                    "Si no has sido tu, puedes ignorar este mensaje."
            );
            case APPOINTMENT_CONFIRMATION -> new TemplateResult(
                    "Cita confirmada",
                    "Tu cita el " + safeData.getOrDefault("date", "") +
                    " a las " + safeData.getOrDefault("time", "") +
                    " con " + safeData.getOrDefault("employee", "") +
                    " ha sido confirmada."
            );
            case APPOINTMENT_REMINDER -> new TemplateResult(
                    "Recordatorio de cita",
                    "Recordatorio: tienes una cita el " + safeData.getOrDefault("date", "") +
                    " a las " + safeData.getOrDefault("time", "") + "."
            );
            case APPOINTMENT_CANCELLATION -> new TemplateResult(
                    "Cita cancelada",
                    "Tu cita del " + safeData.getOrDefault("date", "") + " ha sido cancelada."
            );
            case PAYMENT_FAILED -> new TemplateResult(
                    "Problema con tu pago",
                    "No hemos podido procesar tu pago. Por favor actualiza tu método de pago."
            );
            case SUBSCRIPTION_CANCELED -> new TemplateResult(
                    "Suscripción cancelada",
                    "Tu suscripción a Rivoo ha sido cancelada."
            );
        };
    }
}
