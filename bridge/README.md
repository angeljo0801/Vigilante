# Vigilante Bridge (Playwright opcional)

Ejecutar en un PC dedicado, actualizado, como usuario sin privilegios. No requiere suscripción. Requiere Node.js 22+ y Chromium de Playwright. No abrir el puerto a Internet. Para acceso desde Android utiliza HTTPS privado, por ejemplo Tailscale Serve con ambos dispositivos en la misma tailnet (no Funnel). No usar para eludir restricciones, CAPTCHA o límites del sitio.

PowerShell desde la carpeta bridge:

    npm install
    npx playwright install chromium
    npm test
    $env:VIGILANTE_TOKEN = node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
    $env:VIGILANTE_ALLOWED_HOSTS = "example.com,www.example.com"
    npm start

Guarda el token en un gestor privado (puedes consultarlo en tu terminal antes de iniciar), configura los dominios exactos autorizados incluyendo CDN/API que la página necesite. En Linux instala dependencias con npx playwright install --with-deps chromium. Usa un usuario sin privilegios; no desactives el sandbox. Configura un proxy HTTPS privado al puerto local 8787. En la app abre Configurar Playwright remoto, pega URL HTTPS y token y prueba conexión. No se despliega automáticamente ni crea gastos.

La lista de dominios se aplica también a recursos secundarios y se rechazan IPs no públicas. Como el navegador resuelve DNS de nuevo, estas comprobaciones no constituyen una frontera de seguridad completa frente a DNS rebinding; no autorices dominios no confiables, ejecuta aislado de servicios sensibles y con restricciones de salida de red. No exponer como servicio multiusuario. Las conexiones se limitan a una tarea simultánea, sin cookies persistentes ni scripts externos de usuario. El navegador se ejecuta solamente para lecturas autorizadas; si el sitio devuelve una restricción, se detiene.
