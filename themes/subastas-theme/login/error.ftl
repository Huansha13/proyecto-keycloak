<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false displayMessage=false; section>
    <#if section = "header">

    <#elseif section = "content">
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
                    <div class="login-form">
                        <img src="https://stacdsmf.z13.web.core.windows.net/imagenes/logo-ebiz-b2m.svg" alt="logo eBIZ B2M">

                        <div class="login-form__content">
                            <div>
                                <h1 class="title">Algo salió mal</h1>
                                <p class="text">${message.summary!'No pudimos procesar tu solicitud. Por favor, intenta nuevamente.'}</p>
                            </div>

                            <a class="forgot-password" href="${properties.frontendUrl!'/'}">Volver al inicio de sesión</a>
                        </div>
                    </div>

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
