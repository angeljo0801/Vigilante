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
1. Instala la APK firmada creada por GitHub Actions (pestaña Actions, flujo "Compilar APK Android", archivo descargable de la ejecución).
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
    gradle :app:assembleDebug  # solo pruebas locales; no distribuir

GitHub Actions compila automáticamente en cada push a main y cuando ejecutas el flujo manualmente. Descarga el artefacto Vigilante-APK de la ejecución completada.

## Ejemplo de reglas
- URL de tienda + palabra Disponible + APARECE: alerta cuando el producto pase de no disponible a disponible.
- Selector .estado + palabra Entregado + APARECE: alerta cuando ese estado aparezca.
- Selector #precio + CAMBIA: alerta cuando el texto de ese elemento se modifique.

No se incluyen cuentas, contraseñas ni claves en el repositorio.

## Versión 2.0 — Scraping programado
- Horas diarias locales HH:mm separadas por comas, o intervalos de 15 min a 24 h.
- Extracción por CSS, atributo, texto/JSON y regex opcional (primer grupo capturado o coincidencia completa).
- Alertas en cada lectura, cambios, aparición, desaparición y cruce de umbral numérico. El umbral también avisa en la primera lectura si se cumple.
- Historial local de 50 lecturas por vigilancia, hasta 8000 caracteres cada una, incluyendo errores; botón copiar y actualización del dashboard.
- AlarmManager programa la hora; WorkManager ejecuta con red. Activa Alarmas y recordatorios en Android para mayor precisión. No garantiza puntualidad, especialmente sin conexión, con ahorro de batería o teléfono apagado. Tras reinicio se reprograman las tareas.
- No requiere servidor ni suscripción. Solo sitios accesibles por HTTP(S), respetando permisos, términos y límites. No ejecuta JS dinámico ni elude CAPTCHA.
- Conserva applicationId y configuración anterior; la instalación sobre una versión existente requiere la misma firma.

## Firma permanente de actualizaciones (2.0.1+)
Configura cuatro secretos de Actions: ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS y ANDROID_KEY_PASSWORD. Usa siempre el mismo almacén y alias. Conserva una copia privada fuera del repositorio. El flujo falla explícitamente si faltan secretos, nunca publica una APK firmada con una clave temporal. Para generar una nueva clave o reutilizar una existente, ejecuta tools/setup-signing.ps1 en Windows con Java y GitHub CLI instalados. La clave anterior es imprescindible si deseas actualizar una APK ya instalada con esa firma; una nueva clave no puede reemplazarla. El ID de aplicación se mantiene com.vigilante.app.

La APK firmada se publica como artefacto Vigilante-APK tras verificar su certificado.
