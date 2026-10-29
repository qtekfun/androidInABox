# Spec 08: Play Integrity. Requisitos

Estado: Borrador
Fecha: 2026-10-29
Tipo: Medición
Base: [riesgos-android R13](../../fase0/riesgos-android.md),
[plan de pruebas paso h](../../fase0/plan-pruebas.md).
Depende de: spec 03 (GMS y Play Store funcionando en el box).

## Objetivo

Medir qué veredicto de Play Integrity obtienen las apps dentro del box, registrarlo en
cada hito, y entender qué factores lo limitan. La banca u otras apps con requisitos
estrictos son "best effort": no hay umbral de éxito, aunque se considera
**aceptable** `MEETS_BASIC_INTEGRITY` (plan de pruebas, paso h).

## Requisitos

### R08.1 Aplicación de medida
THE equipo SHALL usar una única app comprobadora de Play Integrity, elegida por el
propietario, de la misma versión en todas las mediciones y registrada en el informe.
Verificación: plan de pruebas paso h; nombre y versión de la app en la tabla de
resultados.

### R08.2 Medición de referencia
WHEN se mida dentro del box THE equipo SHALL medir también fuera del box, en el mismo
dispositivo, como referencia (OPPO con GMS real; el móvil de ROM china no tiene
referencia y se anota "sin GMS").
Verificación: tabla con dos columnas (fuera/dentro) por dispositivo.

### R08.3 Qué se registra
THE informe SHALL registrar por medición: fecha, dispositivo, versión de Android,
build del motor, versión de GmsCore y Play Store, veredicto del dispositivo
(`MEETS_BASIC_INTEGRITY`, `MEETS_DEVICE_INTEGRITY`, `MEETS_STRONG_INTEGRITY`, o error),
`appRecognitionVerdict` y la cadena de error si no hay veredicto.
Verificación: plantilla `resultados.md` rellenada en cada medición.

### R08.4 Medición por hito
WHEN se alcance cada uno de los hitos M3 (spike de GMS), M4 (cadena crítica) y la
versión candidata de cada release THE equipo SHALL repetir la medición en Android 16 y 17.
Verificación: filas por hito en `resultados.md`.

### R08.5 Error sin veredicto
IF la app comprobadora no obtiene veredicto THEN THE equipo SHALL registrar el código de
error y los logs (`IntegrityService|StandardIntegrity|ExpressIntegrity|DroidGuard|PlayCore`)
sanitizados.
Verificación: `adb logcat -d | grep -iE "IntegrityService|StandardIntegrity|ExpressIntegrity|DroidGuard|PlayCore"`.

### R08.6 Análisis de factores
THE equipo SHALL medir por separado el efecto de: (a) propiedades de VirtualSpoof
(Pixel 6 / Android 12 frente al SDK real), (b) UID compartido con el host, (c)
DroidGuard (si el módulo carga y devuelve un token) y (d) Play Store y GMS virtuales
frente a ninguno.
Verificación: matriz de experimentos (design §3) con una medición por celda.

### R08.7 Mejoras opcionales
WHERE una mejora cumpla las reglas de R08.9 THE equipo MAY aplicarla (p. ej. coherencia
de propiedades) y SHALL medir su efecto antes y después.
Verificación: dos filas (antes/después) en `resultados.md`.

### R08.8 No regresión del resto
WHEN se cambien las propiedades para mejorar la coherencia THE equipo SHALL comprobar
que GMS, FCM y las apps invitadas siguen funcionando (plan de pruebas b a d).
Verificación: pasos b, c, d en verde tras el cambio.

### R08.9 No objetivos
THE proyecto SHALL NOT implementar: suplantación de claves o certificados de
atestación (hardware-backed), emulación de un Keystore/TEE, inyección de módulos para
falsear el veredicto, reutilización de respuestas de otro dispositivo ni evasión de
las comprobaciones del servidor de Google.
Verificación: revisión de diseño antes de cada tarea de mejora (lista de comprobación en
design §6).

### R08.10 Banca
THE documentación SHALL indicar que las apps bancarias y de pago pueden rechazar el
box y que no se garantiza su funcionamiento; las pruebas con apps reales son
voluntarias y sin credenciales en los informes.
Verificación: texto en el informe; sin capturas con datos personales.

### R08.11 Resultado expuesto a spec 03
IF el veredicto es "sin veredicto" por una causa de GMS (no de coherencia) THEN
el equipo SHALL abrir una incidencia en la spec 03 con los logs.
Verificación: enlace cruzado en el informe.

## Fuera de alcance

Bypass de atestación, `STRONG_INTEGRITY` como objetivo, certificación del
dispositivo (`ro.build.fingerprint` real aprobado), root.
