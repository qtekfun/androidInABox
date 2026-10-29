# Fase 0: veredicto

Fecha: 2026-10-29. Base: NewBlackbox `89b5983` sin modificar.

Evidencias:
- [resultados-dispositivo.md](resultados-dispositivo.md): OPPO CPH2841, Android 16.
- [arquitectura-motor.md](arquitectura-motor.md)
- [riesgos-android.md](riesgos-android.md)

## Respuesta corta

**Sí es viable, pero no tal cual.**

- **Funciona en Android 16:** el núcleo de virtualización (proxies de binder, procesos
  stub, actividades, IO) funciona sin portarlo. No hace falta reescribir el motor.
- **Falla, y no por Android 16:** cuatro carencias propias del motor, presentes en
  cualquier versión, rompen la cadena crítica:
  - splits;
  - GMS dentro del box;
  - acciones de notificación;
  - identificación de la app en la notificación.
- **El riesgo de versión** se concentra en Android 17 o posterior: `ClientTransaction`
  y la estructura de ART.

## Qué funciona (medido)

- **Compilación:** con JDK 21 y NDK 29, sin cambios. Las `.so` están alineadas a 16 KB.
- **Motor en Android 16:** se instala y arranca, con targetSdk 28.
- **APK único dentro del box:** se instala y su actividad se lanza.
- **Notificaciones:** las de la app invitada llegan a la bandeja del sistema real.
  Tocarlas abre la app invitada.
- **WhatsApp clonado del sistema:** arranca hasta la pantalla de bienvenida.
- **API ocultas:** el acceso a la greylist está permitido, aunque el bypass nativo falla.

## Qué falla

| Paso del plan | Estado upstream | Causa | Arreglo |
|---|---|---|---|
| a. Motor en Android 16 | OK | — | Quitar el diálogo de VPN que sale en cada arranque |
| b. GMS / microG dentro | **Falla** | Splits no soportados; providers GMS desviados al GMS real; GMS crashea (cronet); sin spoof de firma para microG | R1 + R2 |
| c. Cuenta de Google | **Falla** (depende de b) | — | R2 |
| d. WhatsApp y Gmail reciben | **Falla** | Sin FCM (b); procesos sin servicio en primer plano; Gmail necesita splits | R1, R2, R9 |
| e. Notificación con nombre de app | **Parcial** | Cabecera "BlackBox", sin app ni perfil, icono incorrecto, tags pisados | R4 |
| f. Responder desde notificación | **Falla** | PendingIntents de broadcast y servicio sin reescribir | R3 |
| g. Reloj | Llega como "BlackBox" (hipótesis); responder falla | Huawei Health filtra por paquete emisor | R3 + R4 (y ver la decisión 3) |
| h. Play Integrity | Sin veredicto | Depende de b; además, la suplantación Pixel 6 / Android 12 es incoherente | R13: como mucho BASIC |

## Esfuerzo estimado

Cifras en días de un desarrollador Android con experiencia en el framework. El detalle
de puntos de parche está en [alcance-parches.md](alcance-parches.md).

| Trabajo | Días | Confianza |
|---|---|---|
| Soporte de split APK (clonar del host e instalar `.apks`/`.apkm`) | 6–8 | media |
| PendingIntent de broadcast/servicio + fill-in de RemoteInput | 4–5 | media-alta |
| Presentación de notificaciones (icono, tag, canal por paquete, app y perfil) | 3–4 | media-alta |
| microG: spoof de firma, sin passthrough de providers, proceso vivo | 8–12 | baja-media |
| Proceso core con servicio en primer plano y exención de batería (R9) | 4–6 | media |
| HiddenApiBypass (R5), `getTransactionItems` (R6), comprobación de JniHook (R7) | 4–6 | media |
| Limpieza del fork: telemetría (ADR-0003), VPN, propiedades falsas | 2–3 | alta |
| Estabilización y pruebas en Android 13, 14, 15, 16 y 17 | 15–20 | baja |
| **Total, ruta microG** | **≈ 46–64 días (2,5–3 meses)** | baja-media |
| GMS real de Google dentro del box (adicional, investigación) | ≥ 20–40, sin garantía | baja |

**Portar el motor a Android 16 no tiene coste específico:** ya funciona.
**Android 17:** 2–3 días de `ClientTransaction` más el seguimiento de ART, incluidos en
la tabla. Hay que repetir el plan cuando haya un dispositivo con Android 17.

## Recomendación: **seguir**, con un cambio de enfoque en GMS

1. **Seguir con el fork de NewBlackbox.** No hace falta pivotar de motor. Los fallos
   están en capas acotadas y arreglables: instalador, `GetIntentSender` y el servicio
   de notificaciones.
2. **Priorizar microG frente al GMS real** para el caso de la ROM china.
   - Dentro de un sandbox, el motor controla el `PackageManager`, así que el spoof de
     firma que microG necesita es un parche pequeño.
   - El GMS real exige privilegios de sistema que ningún motor abierto conocido emula de
     forma estable.
   - El GMS real queda como investigación paralela y acotada en el tiempo.
3. **Orden propuesto para la fase 1:**
   1. telemetría y limpieza;
   2. splits;
   3. notificaciones (R3 + R4) validadas con NotifProbe;
   4. microG;
   5. supervivencia en segundo plano;
   6. el plan completo en Android 16, y luego en 13, 14, 15 y 17.
4. **Criterio para parar** al final de la fase 1: si con microG dentro WhatsApp no
   recibe push de forma fiable (paso d) tras el trabajo de R9, el objetivo principal
   no se cumple con este enfoque y habría que replantearlo.

## Decisiones que necesito del propietario

1. **GMS real o microG como ruta principal** de la fase 1. Recomiendo microG.
2. **Dispositivo con ROM china para validar el caso 1.** Esta fase se ha medido en una
   ROM EEA que ya trae GMS. ¿Qué dispositivo y ROM tienes para eso?
3. **Reloj:** Huawei Health filtra por paquete emisor, así que todas las apps del box se
   activan o desactivan en bloque. ¿Es aceptable, o hace falta poder elegir por app?
   Elegir por app implicaría apps satélite por perfil: más complejidad y más APKs que
   instalar.
4. **Nombre e identificador del paquete del fork**, que hoy es `top.niunaijun.blackbox`.
   Hay que cambiarlo antes del primer release.
