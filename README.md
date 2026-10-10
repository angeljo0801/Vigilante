# Vigilante — monitor de páginas web para Android

Vigilante te avisa cuando una página pública cumple una condición. Puedes crear varias vigilancias, activar/desactivar cada una y comprobar manualmente si hay una coincidencia.

## Funciones de la versión 1
- URL, nombre descriptivo y texto objetivo (sin distinguir mayúsculas/minúsculas).
- Opcional: selector CSS para observar solo un elemento de la página, como .availability o #status.
- Opcional: atributo HTML que quieras examinar en lugar del texto, como class, title, aria-label o data-status.
- Condiciones: APARECE (de no encontrado a encontrado), DESAPARECE o CAMBIA el contenido observado.
- Intervalos configurables de 15, 30, 60 o 120 minutos.
- Monitorización en segundo plano con Android WorkManager y red disponible, incluso después de cerrar la pantalla de la app.
- Alerta de alta prioridad con sonido de alarma, vibración y enlace al sitio. Requiere permiso de notificaciones.
- Historial breve del último estado, última comprobación y mensajes de error por vigilancia.
- Botón "Comprobar ahora" y otro para probar la alarma.
- Guarda la configuración localmente en tu dispositivo.

## Cómo usarla
1. Instala la APK de depuración creada por GitHub Actions (pestaña Actions, flujo "Compilar APK Android", archivo descargable de la ejecución).
2. Abre Vigilante, permite las notificaciones y toca "Nueva vigilancia".
3. Escribe una URL completa como https://ejemplo.com/producto y un texto como "Disponible".
4. Si el sitio contiene textos repetidos, añade un selector CSS específico para su estado.
5. Guarda. La primera lectura establece el estado inicial sin alarma; si después cambia como pediste, recibirás un aviso.
6. Con "Comprobar ahora" puedes probarlo sin esperar al siguiente ciclo.

## Límites importantes
- Esta versión descarga HTML y texto accesibles por HTTP/HTTPS; no inicia sesión ni ejecuta JavaScript dinámico de un navegador.
- Los sitios con CAPTCHA, protección anti-bots, estados que solo aparecen después de ejecutar JavaScript o contenido privado pueden requerir una segunda modalidad con navegador automatizado y un servidor.
- Android impone un intervalo mínimo de 15 minutos a WorkManager y puede retrasar comprobaciones para ahorrar batería. El intervalo configurado NO es una garantía de puntualidad.
- Si necesitas comprobaciones cada minuto y alertas aun con el móvil apagado, hace falta un monitor remoto (por ejemplo en Railway) y notificaciones push.
- No evita mecanismos de acceso ni elude verificaciones de los sitios web. Respeta sus términos y límites.

## Compilar
Proyecto Android Java. Abre esta carpeta en Android Studio para sincronizar con Gradle, o usa una instalación de Gradle 8.9 y JDK 17:
    gradle :app:assembleDebug

GitHub Actions compila automáticamente en cada push a main y cuando ejecutas el flujo manualmente. Descarga el artefacto Vigilante-APK de la ejecución completada.

## Ejemplo de reglas
- URL de tienda + palabra Disponible + APARECE: alerta cuando el producto pase de no disponible a disponible.
- Selector .estado + palabra Entregado + APARECE: alerta cuando ese estado aparezca.
- Selector #precio + CAMBIA: alerta cuando el texto de ese elemento se modifique.

No se incluyen cuentas, contraseñas ni claves en el repositorio.
