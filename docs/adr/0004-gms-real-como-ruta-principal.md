# ADR-0004: GMS real de Google como ruta principal

- Estado: aceptada (decisión del propietario)
- Fecha: 2026-10-29

## Contexto

La fase 0 midió en Android 16 que el GMS clonado dentro del box crashea en bucle
(cronet, `jni_android.cc:220`). También vio que el motor desvía los providers de GMS al
GMS real del sistema y que no soporta split APK. El veredicto recomendaba microG por
ser más fácil de virtualizar. El propietario prefiere el GMS real y dispone de APKs
de GMS para probar.

## Decisión

- La ruta principal es **ejecutar el GMS real de Google** (GmsCore, GSF y Play Store)
  dentro del espacio virtual.
- Los APKs los aporta el usuario y **nunca entran en el repositorio**.
- microG queda como alternativa de respaldo, no como objetivo.

## Consecuencias

- Es la parte de mayor riesgo y esfuerzo: al menos 20–40 días de trabajo adicionales y
  sin garantía de éxito. El plan debe empezar por una **prueba acotada en el tiempo**
  con criterio de salida.
- Son requisitos previos:
  - soporte de split APK y de bundles (`.apks`/`.apkm`);
  - GMS virtual sin desvío de providers al sistema real;
  - un proceso persistente para GMS;
  - emular los permisos `signature|privileged` que GMS espera.
- Android Auto y Wear OS (ADR-0005) dependen de esta decisión.
