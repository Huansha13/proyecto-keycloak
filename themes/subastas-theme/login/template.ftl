<#macro registrationLayout bodyClass='' displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.ico">
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet">
        </#list>
    </#if>
</head>
<body class="${bodyClass}">
    <#if displayMessage && message?has_content>
        <div class="kc-alert-floating <#if message.type == 'success'>alert-success<#elseif message.type == 'warning'>alert-warning<#elseif message.type == 'error'>alert-error<#else>alert-info</#if>" id="kc-alert-container" onclick="this.style.display='none'">
            <#if message.header?has_content><strong>${message.header}</strong><br></#if>
            ${kcSanitize(message.summary)?no_esc}
            <button class="kc-alert-floating__close" onclick="this.parentElement.style.display='none'" aria-label="Cerrar">&times;</button>
        </div>
    </#if>
    <#if messagesPerField??>
        <#list ["username","email","password","password-new","password-confirm"] as field>
            <#if messagesPerField.existsError(field)>
                <div class="kc-alert-floating alert-error" onclick="this.style.display='none'">
                    ${kcSanitize(messagesPerField.get(field))?no_esc}
                    <button class="kc-alert-floating__close" onclick="event.stopPropagation();this.parentElement.style.display='none'" aria-label="Cerrar">&times;</button>
                </div>
            </#if>
        </#list>
    </#if>
    <#nested "header">
    <#nested "content">
    <#nested "info">
    <script>
    (function(){
        var alerts = document.querySelectorAll('.kc-alert-floating');
        alerts.forEach(function(el){
            setTimeout(function(){
                el.classList.add('kc-alert-dismissing');
                setTimeout(function(){ el.style.display = 'none'; }, 300);
            }, 5000);
        });
    })();
    </script>
</body>
</html>
</#macro>
