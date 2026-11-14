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

## 2.1 — Explorador web
Pega una URL en Explorar una página web. Se detectan hasta 100 campos HTML públicos mediante reglas heurísticas: títulos, precios, tasas, estados, fechas, tablas, enlaces, metadatos y textos. Puedes filtrar resultados y seleccionar varios sucesivamente; el formulario se rellena con un selector CSS verificado contra la página y el atributo o patrón correspondiente. Los valores numéricos simples se ofrecen por separado para alertas de umbral. No utiliza IA ni servicios externos. Los selectores pueden requerir revisión si cambia la estructura del sitio. Las páginas JS/CAPTCHA y las API JSON complejas no se desglosan en esta versión.

## 2.2 — Motor JavaScript
Explorador: modo Navegador (predeterminado) y HTML directo. El navegador Android carga JavaScript, espera 3/8/15/25 segundos tras terminar la navegación y analiza una instantánea del DOM. Cada elemento seleccionado guarda su motor y espera junto con sus alarmas y horarios independientes; WorkManager utiliza el mismo motor al ejecutar la tarea. Se amplía el explorador a 200 campos y se distingue entre elementos diferentes aunque tengan el mismo valor. Sin IA ni servidor. No evita CAPTCHA, bloqueos o autenticación; no extrae automáticamente iframes de origen diferente, canvas, shadow DOM cerrado ni contenido que exige interacción o espera superior al límite. La ejecución en segundo plano depende de Android y WebView.

## 2.3 — Intervención humana
Cuando se detecta una posible verificación humana (CAPTCHA) o restricciones HTTP 401/403/429, la vigilancia registra el problema, envía una notificación y no realiza nuevas consultas automáticas hasta que una comprobación manual tenga éxito. La notificación y el dashboard abren una vista de navegación manual sin extracción, sin resolver CAPTCHA automáticamente ni ignorar restricciones. El usuario puede volver y reintentar cuando el sitio permita el acceso. El detector es heurístico y puede no identificar todas las verificaciones; algunos sitios no admiten WebView ni automatización. Las restricciones se mantienen si el sitio sigue bloqueando. El explorador también muestra la acción requerida.

## 3.0 — Motor híbrido
Automático combina HTML y WebView para descubrir datos; Playwright se usa explícitamente o cuando ninguno devuelve campos. Cada elemento conserva su motor de origen; las vigilancias automáticas solo escalan cuando falta el dato, nunca ante restricciones de acceso o errores de red. Playwright requiere configurar un puente privado HTTPS en el PC (carpeta bridge). Sigue funcionando sin puente. No se crean servicios de pago. Consulta bridge/README.md. Ningún motor garantiza acceso a todos los sitios.

## 3.1 — Importar búsquedas desde ChatGPT
Comparte texto desde Android hacia Vigilante o copia el bloque a Importar búsqueda de ChatGPT. Formato VIGILANTE/1: una línea por campo NOMBRE, URL, OBJETIVO, REGLA (CHANGES/ALWAYS/APPEARS/DISAPPEARS/ABOVE/BELOW), PALABRA, HORAS (HH:mm separadas por comas, cada día en hora local), INTERVALO (15/30/60/120/360/720/1440 minutos), MOTOR (AUTO/HTML/WEBVIEW/PLAYWRIGHT), CSS, ATRIBUTO, REGEX, ESPERA (3/8/15/25 s); termina con FIN. Los campos opcionales pueden omitirse. Una URL o texto normal crea un borrador sin inferir el horario. Se puede elegir un elemento en el explorador conservando la regla, palabra y horario importados o editar directamente. No crea alarmas hasta Guardar. El objetivo es una nota, no una búsqueda semántica ejecutada por IA. No hay conexión automática con la lista de tareas de ChatGPT ni soporte de calendarios semanales; las horas son diarias. Mantiene los motores y firma previos.

## 3.2 — Recepción directa de ChatGPT
En el dashboard abre Conectar ChatGPT · envío directo. Activa la autorización y copia el código público de vinculación, envíalo a ChatGPT una sola vez. La clave privada RSA se conserva en AndroidKeyStore. ChatGPT puede cifrar una configuración VIGILANTE/1 mediante tools/encrypt-inbox.py y escribir el sobre cifrado en la rama inbox, archivo inbox/<huella SHA256 de clave pública>.json (array de hasta 100 sobres; no subir texto original). El acceso de escritura al repo es el control de remitente; el repo es público pero solo el teléfono vinculado descifra el contenido. La app sincroniza al abrirse, con botón manual y WorkManager cada 15 minutos aproximadamente. Android puede retrasar el trabajo. No es push instantáneo. Configuraciones completas se activan automáticamente; las que no definen el dato o horario con precisión quedan como borradores. Desactivar la recepción no elimina vigilancias ya recibidas. Reinstalar o borrar datos puede requerir nueva vinculación. No requiere Railway ni suscripción adicional. No sincroniza las tareas internas de ChatGPT automáticamente.

## 3.3 — Valor fijado y tendencia
Las vigilancias con lecturas numéricas inequívocas muestran por defecto una notificación silenciosa persistente con el último valor, flecha y diferencia respecto a la lectura anterior válida, y hora de lectura. Las alarmas habituales incluyen la tendencia. Cada vigilancia permite desactivar el valor fijado y configurar su unidad; USD/CUP en el nombre sugiere CUP. La primera lectura posterior a la actualización establece una nueva referencia, sin usar lecturas antiguas potencialmente erróneas. Si falla la consulta, se conserva el último valor válido marcado como no actualizado. Pausar/borrar retira la notificación. Android permite ocultar o descartar notificaciones según sus ajustes. Importación admite UNIDAD y FIJAR: SI/NO. No cambia la fuente de información ni garantiza la precisión de la regla de extracción.

## 3.4 — Precio en el panel de ajustes rápidos
En el dashboard pulsa Añadir precio al panel de control, selecciona la vigilancia y confirma Añadir al panel. En Android 13+ se solicita al sistema la adición del acceso; también puedes editar el panel con el lápiz y arrastrar Vigilante · Precio. El acceso muestra el último número válido, su flecha y, cuando el sistema muestra etiquetas, la unidad y diferencia. Para paneles compactos con iconos solos, el icono monocromo incluye el número si tiene hasta cinco caracteres y el indicador. Si falla una consulta, muestra estado no actualizado. Al tocar abre Vigilante. El sistema define tamaño, colores, posición y visibilidad de etiquetas; no se altera SystemUI ni requiere root. El modo automático prefiere la última vigilancia activa USD/CUP; puedes escoger una específica. Se actualiza al guardar lecturas y al abrir los ajustes rápidos. Conserva el funcionamiento y las notificaciones previas.

## 3.5 — Alertas universales en ajustes rápidos
Hasta seis accesos independientes de Android Quick Settings. Cada uno puede apuntar a cualquier vigilancia, no solo números: fecha, texto, disponibilidad, estado, título, etc. Usa Añadir alertas al panel de control o Añadir esta alerta al panel desde la tarjeta de vigilancia. Se muestra el último resultado válido, una flecha para valores numéricos, indicador de cambio para textos y estado de error/pausa. Los iconos compactos muestran un máximo de 12 caracteres; tocar abre el último resultado completo conservado en historial (hasta 8000 caracteres), y permite cambiar la selección. Android decide el tamaño/orden, puede truncar las etiquetas y requiere confirmar cada acceso. Se conserva la selección existente del primer acceso. El automático usa la lectura más reciente sin preferir USD/CUP. No crea accesos ilimitados; seis disponibles.

## 3.5.1 — Corrección del icono de ajustes rápidos
Los accesos pasan al modo no activo de TileService: SystemUI puede enlazarlos cada vez que abre el panel. Se redibujan desde el almacenamiento local al iniciar la escucha y cuando reciben cambios mientras el panel está abierto. El icono usa un PNG independiente con el valor más grande, recupera lecturas anteriores guardadas y muestra `—` sin dato o `?` sin asignación en lugar del gráfico genérico. No vuelve a consultar la web al abrir el panel. El formato final depende de Samsung; las pruebas de compilación no garantizan el comportamiento de SystemUI de cada modelo.
