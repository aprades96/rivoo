<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>

    <#if section = "header">
        <div class="rivoo-header">
            <img id="rivoo-logo" src="${url.resourcesPath}/img/rivoo-logo.svg" alt="Rivoo" class="rivoo-logo" />
            <h1 id="rivoo-salon-name" class="rivoo-title">Rivoo</h1>
            <p class="rivoo-subtitle">Inicia sesion en tu cuenta</p>
        </div>
    <#elseif section = "form">
        <#if realm.password>
            <form id="kc-form-login" onsubmit="login.disabled = true; return true;"
                  action="${url.loginAction}" method="post">

                <#if messagesPerField.existsError('username','password')>
                    <div class="rivoo-alert rivoo-alert-error">
                        ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                    </div>
                </#if>

                <div class="rivoo-field">
                    <label for="username" class="rivoo-label">Correo electrónico</label>
                    <input id="username" name="username" type="text" class="rivoo-input"
                           value="${(login.username!'')}" autocomplete="username" autofocus />
                </div>

                <div class="rivoo-field">
                    <label for="password" class="rivoo-label">Contraseña</label>
                    <input id="password" name="password" type="password" class="rivoo-input"
                           autocomplete="current-password" />
                </div>

                <div class="rivoo-options">
                    <#if realm.rememberMe && !usernameHidden??>
                        <label class="rivoo-checkbox">
                            <input id="rememberMe" name="rememberMe" type="checkbox"
                                   <#if login.rememberMe??>checked</#if> />
                            <span>Recuerdame</span>
                        </label>
                    <#else>
                        <span></span>
                    </#if>
                    <#if realm.resetPasswordAllowed>
                        <a class="rivoo-link" href="${url.loginResetCredentialsUrl}">¿Olvidaste tu contraseña?</a>
                    </#if>
                </div>

                <input type="hidden" id="id-hidden-input" name="credentialId"
                       <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if> />

                <button class="rivoo-btn" name="login" id="kc-login" type="submit">
                    Iniciar sesion
                </button>
            </form>
        </#if>
    <#elseif section = "info">
        <div class="rivoo-info">
            <span>¿No tienes cuenta?</span>
            <a href="${url.registrationUrl}">Crear cuenta</a>
        </div>
    </#if>

</@layout.registrationLayout>
