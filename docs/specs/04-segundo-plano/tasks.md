# Spec 04: segundo plano y push (FCM). Tareas

Estado: Borrador

Cada tarea: ≤ 1 día salvo indicación, un commit pequeño, CI en verde. Rutas relativas a `engine/`
(`B` = `Bcore/src/main/java/top/niunaijun/blackbox/`, `A` = `app/src/main/java/top/niunaijun/blackboxa/`).
Las pruebas en dispositivo las ejecuta el propietario y devuelve la carpeta de `scripts/fase0/diag.sh`.
Dependencias: T04.11 y T04.12 necesitan T02.3, T02.8 y T02.10; T04.16 necesita la spec 03.

## Medición y spike

**T04.1** Spike de prioridad de procesos: medir con `dumpsys activity processes <host>` la importancia de un
`:pN` (app de prueba cualquiera) tras 60 min con pantalla apagada en cuatro casos: D (solo servicio central),
A (bind desde `:black`), B (proveedor inestable), C (primer plano propio). Criterio de salida en el diseño §6.2.
- Cubre: R04.14.
- Verificación: tabla de resultados en `design.md` §6.2 (importancia y estado por caso).
- Estimación: 1 día (más 1 h de espera por caso, la ejecuta el propietario).

**T04.2** Herramienta de medición de latencia `tools/pushprobe/` (receptor FCM de prueba, `build.sh`,
`README`, plantilla de configuración sin claves) y script de comparación de horas.
- Cubre: R04.24.
- Verificación: fuera del box en el OPPO con GMS real mide retardo de 10 mensajes (línea base).
- Estimación: 1 día.

## Servicio central

**T04.3** Refactor de `DaemonService`: tipo `specialUse` en `startForeground`, textos en `strings.xml` (es/en),
`contentIntent` al host, propiedad de subtipo en el manifiesto, retirar la cancelación de `DaemonInnerService`.
- Cubre: R04.1, R04.2, R04.3.
- Verificación: `dumpsys activity services <host>` con `isForeground=true`; notificación visible 5 min.
- Estimación: 1 día.

**T04.4** `CoreServiceLauncher.ensureRunning` y sustituir los tres bucles de `BlackBoxCore`; registro del
motivo de fallo y reintento al volver a primer plano.
- Cubre: R04.4, R04.5.
- Verificación: forzar bucket `restricted`; logcat con el motivo; arranque correcto al abrir la app.
- Estimación: 1 día.

**T04.5** `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) y ajuste "iniciar con el sistema".
- Cubre: R04.6, R04.7.
- Verificación: `adb reboot`, desbloquear; servicio activo sin abrir la app.
- Estimación: 1 día.

## Batería y ROM

**T04.6** Flujo de exención de batería en el host (`PowerGuide`, permiso `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`,
aviso permanente) integrado en `WelcomeActivity`.
- Cubre: R04.9, R04.10.
- Verificación: `dumpsys deviceidle whitelist | grep <host>`; revocar con `-<host>` y comprobar el aviso.
- Estimación: 1 día.

**T04.7** Guía de ROM: `rom_intents.json`, detección de fabricante, `BackgroundGuideActivity`, marcas del
usuario, respaldo a ficha de app, prueba unitaria con `PackageManager` falso.
- Cubre: R04.11, R04.12, R04.13.
- Verificación: prueba unitaria; ejecutar en ColorOS (OPPO) y en la ROM china; captura sin datos personales.
- Estimación: 1,5 días.

## Procesos invitados

**T04.8** `KeepAliveManager`: política persistida en `keepalive.json`, ampliación de `IBProcessManagerService`
(AIDL) y cliente en `BlackBoxCore`; por defecto GMS y GSF.
- Cubre: R04.14, R04.16.
- Verificación: test instrumentado de guardado y lectura; reinicio de `:black` conserva la lista.
- Estimación: 1 día.

**T04.9** Mecanismo de prioridad elegido en T04.1 (A: `bindService` con acción `bb.KEEPALIVE` y
`AppServiceDispatcher.onBind` que la ignora, o el que salga del spike).
- Cubre: R04.14.
- Verificación: `dumpsys activity processes <host>`: `:pN` fuera de caché tras 60 min con pantalla apagada.
- Estimación: 1,5 días.

**T04.10** Reinicio con espera creciente en `onProcessDie` y aviso tras 5 fallos; también R04.8 (restaurar la
política tras `START_STICKY`).
- Cubre: R04.8, R04.15.
- Verificación: `adb shell kill <pid>` del `:pN` marcado; reaparece en < 30 s; 6 muertes seguidas producen el aviso.
- Estimación: 1 día.

**T04.11** `setServiceForeground`: pasar la notificación por `NotificationPresenter` y el mapeo de canal, tipo 0
documentado, y comprobar la subida de prioridad del stub.
- Cubre: R04.18, R04.19, R04.20.
- Verificación: NotifProbe con servicio en primer plano; `dumpsys activity services <host>` muestra `ProxyService$Pn` en primer plano.
- Estimación: 1 día.

**T04.12** `IAlarmManagerProxy`: quitar el `set` vacío y verificar `set*` con PI de broadcast reescrito; caso nuevo de
NotifProbe (alarma a 10 s y a 15 min en Doze).
- Cubre: R04.17.
- Verificación: la notificación de la alarma aparece a su hora; con `force-idle` aparece en la ventana permitida.
- Estimación: 1 día.

## Estado y memoria

**T04.13** Registro de `getHistoricalProcessExitReasons` al arrancar (fichero rotado) y umbral de memoria en
`KeepAliveManager` (parámetro, valor inicial 15 % libre).
- Cubre: R04.22, R04.23.
- Verificación: `dumpsys activity exit-info <host>` coincide con el registro; prueba en el móvil con Android 17.
- Estimación: 1 día.

**T04.14** `StatusFragment` con estado del servicio, exención, pasos de ROM, apps vivas con slots, última razón
de salida y ajustes (R04.5, R04.16).
- Cubre: R04.5, R04.10, R04.16, R04.22.
- Verificación: revisión manual de cada estado en el OPPO.
- Estimación: 1 día.

**T04.15** Tabla de reglas de Android 13 a 17 verificada con `dumpsys` en cada dispositivo y documento de
procedimiento de batería (`dumpsys batterystats`).
- Cubre: R04.21, R04.26.
- Verificación: §3 del diseño sin "(sin verificar)" para los dispositivos probados.
- Estimación: 1 día.

## Aceptación

**T04.16** Prueba de aceptación de push: PushProbe y WhatsApp/Gmail a 1, 10 y 60 min, 10 mensajes cada vez, en
Android 16 y 17 (cierre del hito M4 junto con la spec 03). Si falla, aplicar el criterio de parada del
veredicto (replantear el objetivo) y registrarlo.
- Cubre: R04.25 y, con T04.2, R04.24.
- Verificación: informe de latencias con `scripts/fase0/diag.sh`; 9 de 10 < 60 s y ninguno > 2 min.
- Estimación: 2 días (propietario y desarrollador).

## Resumen

| Bloque | Tareas | Días |
|---|---|---|
| Medición y spike | T04.1, T04.2 | 2 |
| Servicio central | T04.3 a T04.5 | 3 |
| Batería y ROM | T04.6, T04.7 | 2,5 |
| Procesos invitados | T04.8 a T04.12 | 5,5 |
| Estado y memoria | T04.13 a T04.15 | 3 |
| Aceptación | T04.16 | 2 |
| **Total** | | **18 días** |

El veredicto estimaba 4 a 6 días para R9; la diferencia es el spike, la herramienta de medición, las
alarmas, los servicios en primer plano del invitado, la guía de ROM y la aceptación a 60 min.
