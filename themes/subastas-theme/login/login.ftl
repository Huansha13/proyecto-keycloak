<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=social.displayInfo displayMessage=true; section>
    <#if section = "header">

    <#elseif section = "content">
        <#if realm.password && social.providers??>
            <div id="kc-social-providers">
                <ul>
                    <#list social.providers as p>
                        <li>
                            <a id="social-${p.alias}" href="${p.loginUrl}">
                                <#if p.icon??><img src="${p.iconImg}" alt="${p.displayName}"></#if>
                                <span>${p.displayName}</span>
                            </a>
                        </li>
                    </#list>
                </ul>
            </div>
        </#if>

        <header class="login-view__header">
            <img src="https://stacdsmf.z13.web.core.windows.net/imagenes/vector.svg" alt="Subastas">
        </header>

        <section class="login-view">
            <div class="login-view__layout">
                <div class="login-view__aside">
                    <div class="carrousel">
                        <img src="https://aadcdn.msftauthimages.net/dbd5a2dd-1lbea0jz0vz1qazxx2uoheh1kfuo-8kpvwdctjwo07i/logintenantbranding/0/illustration?ts=638957038302810280" alt="Subastas" class="img">
                    </div>
                </div>

                <div class="login-view__content bg-white">
                    <form id="kc-form-login" class="login-form" onsubmit="login.disabled = true; return true;"
                          action="${url.loginAction}" method="post">

                        <img src="https://stacdsmf.z13.web.core.windows.net/imagenes/logo-ebiz-b2m.svg" alt="logo eBIZ B2M">

                        <div class="login-form__content">
                            <div>
                                <h1 class="title">Bienvenido</h1>
                                <p class="text">Ingresa tus credenciales para acceder a tu cuenta.</p>
                            </div>

                            <div class="form-fields">
                                <#if usernameEditDisabled??>
                                    <div class="form-field">
                                        <input tabindex="1" id="username" class="input"
                                               name="username" value="${(login.username!'')}" type="text" disabled
                                               placeholder="Correo electrónico" />
                                    </div>
                                <#else>
                                    <div class="form-field">
                                        <input tabindex="1" id="username"
                                               class="input"
                                               name="username" value="${(login.username!'')}" type="text" autofocus
                                               autocomplete="username"
                                               placeholder="Correo electrónico" />
                                    </div>
                                </#if>

                                <div class="form-field">
                                    <input tabindex="2" id="password"
                                           class="input"
                                           name="password" type="password"
                                           autocomplete="current-password"
                                           placeholder="Contraseña" />
                                </div>

                                <button type="submit" name="login" id="kc-login" class="button button--primary">
                                    Iniciar sesión
                                </button>

                                <#if realm.resetPasswordAllowed>
                                    <a class="forgot-password" href="${url.loginResetCredentialsUrl}">
                                        ¿Olvidó tu contraseña?
                                    </a>
                                </#if>
                            </div>
                        </div>
                    </form>

                    <footer class="footer">
                        <div class="footer__legal">
                            <a class="footer__link" href="https://ebiz.pe/condiciones-generales-de-nuestros-servicios/" target="_blank" rel="noopener">Términos y Condiciones</a>
                            <span class="footer__dot">&middot;</span>
                            <a class="footer__link" href="https://ebiz.pe/politica-de-privacidad-y-de-proteccion-de-datos-personales/" target="_blank" rel="noopener">Políticas de privacidad</a>
                        </div>
                        <p class="footer__copyright">&copy; ${.now?string("yyyy")} eBIZ, todos los derechos reservados.</p>
                        <div class="footer__social">
                            <a href="https://www.facebook.com/ebizlatinamerica" target="_blank" rel="noopener"><img src="https://stacdsmf.z13.web.core.windows.net/imagenes/facebook.svg" alt="Facebook"></a>
                            <a href="https://twitter.com/ebizlatin" target="_blank" rel="noopener"><img src="https://stacdsmf.z13.web.core.windows.net/imagenes/twitter.svg" alt="Twitter"></a>
                            <a href="https://www.youtube.com/channel/UC7jB1YLnjfdVhIyp-kUpK6Q" target="_blank" rel="noopener"><img src="https://stacdsmf.z13.web.core.windows.net/imagenes/youtube.svg" alt="Youtube"></a>
                            <a href="https://www.instagram.com/ebizlatinamerica" target="_blank" rel="noopener"><img src="https://stacdsmf.z13.web.core.windows.net/imagenes/instagram.svg" alt="Instagram"></a>
                        </div>
                    </footer>
                </div>
            </div>
        </section>

    <#elseif section = "info">
    </#if>
</@layout.registrationLayout>
