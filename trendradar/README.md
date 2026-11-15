# TrendRadar · Android
Aplicación separada de Vigilante (com.trendradar.app). Logo radar verde sobre fondo oscuro. Sin servidores de pago.

## Firma
El workflow de TrendRadar reutiliza los cuatro secretos permanentes de firma que ya utiliza Vigilante en este repositorio. No se publica la clave. Mantener el mismo applicationId, certificado y aumentar versionCode para todas las actualizaciones.

## Datos y conexión bidireccional
En Ajustes introduce un token GitHub fine-grained **solo para Vigilante-Sync**, Contents read/write, con expiración. No usar un token clásico ni compartirlo por chat. El token se guarda cifrado con AndroidKeyStore. El investigador autorizado escribe `trendradar-feed.json` y lee/actualiza `trendradar-config.json` en el repo privado. Android descarga el feed, guarda caché, avisa al detectar identificadores nuevos (primera sincronización establece referencia), y permite editar instrucciones del config con control SHA de concurrencia. ChatGPT puede editar código en este repositorio y preferencias en el privado con su conector autorizado. No es un agente que modifique archivos arbitrarios del teléfono ni una conexión directa a la interfaz de ChatGPT. No concede acceso a otros repositorios desde la app.

Feed v1: {"schema":1,"updated_at":"ISO8601","items":[{"id":"id estable por hallazgo","category":"Productos|Innovaciones|Servicios|Empresas|IPO|Cuba","name":"Nombre","country":"País","date":"AAAA-MM-DD","summary":"Descripción","evidence":"Datos observados","opportunity":"Análisis económico","risks":"Riesgos","validation":"Próximo paso","sources":["https://..."]}]}. Hasta 500 elementos y menos de 700 KB. Sin datos simulados.

## Vigilancia
El radar programado de ChatGPT investiga y publica resultados si tiene acceso al conector y permisos. Android WorkManager consulta cada 15 minutos aproximadamente, sujeto a batería, red y restricciones del sistema. La app también sincroniza manualmente. No es push instantáneo ni un scraper de todas las plataformas. No inventa datos si el feed está vacío. La calculadora usa valores del usuario, no predice beneficios.

## Compilación
Gradle 8.9 + JDK 17 + Android SDK 35: `gradle -p trendradar :app:assembleDebug`. Workflow separado para release firmado. APK independiente, no modifica el APK Vigilante.
