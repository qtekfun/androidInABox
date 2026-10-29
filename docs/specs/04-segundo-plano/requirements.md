# Spec 04: segundo plano y push (FCM). Requisitos

Estado: Borrador

Base: R9, R10 y R8 de [riesgos-android](../../fase0/riesgos-android.md), paso d del
[plan de pruebas](../../fase0/plan-pruebas.md) y criterio de parada del
[veredicto](../../fase0/veredicto.md). Hito M4 del [ROADMAP](../ROADMAP.md).
Depende de la spec 03 (GMS operativo) para la prueba de extremo a extremo y de la spec 02
para las notificaciones de servicios en primer plano y las alarmas.

## Historias de usuario

- Como usuario, recibo los mensajes de WhatsApp y Gmail del box con el móvil bloqueado y
  la app cerrada, en menos de un minuto, también tras una hora de inactividad.
- Como usuario de una ROM china, la app me guía para quitar las restricciones de batería y
  de autoarranque, y me dice cuándo falta algo.
- Como usuario, veo si el motor está activo y qué apps del box se mantienen vivas.

## Contexto técnico que fija los requisitos

- El host es un único UID con un proceso `:black` y hasta 50 procesos `:pN` (`ProxyManifest.FREE_COUNT`).
  Un servicio en primer plano en `:black` no sube la prioridad de los `:pN`.
- Hoy: `DaemonService` (en `:black`) llama a `startForeground` sin tipo explícito; `DaemonInnerService`
  intenta cancelar su notificación; `ProxyService` no llama a `startForeground`; `setServiceForeground`
  fuerza el tipo a 0; `IAlarmManagerProxy.set` devuelve sin hacer nada; hay `RECEIVE_BOOT_COMPLETED`
  declarado pero ningún receptor; no hay `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- El host declara `targetSdkVersion 28`. Eso lo exime de la obligación de tipo en servicios en primer
  plano y de los permisos `FOREGROUND_SERVICE_<TIPO>` de Android 14, y de los límites por tipo de
  Android 15 que dependen del target. **No lo exime** de las restricciones de arranque de servicios
  en primer plano desde segundo plano (Android 12+), de la posibilidad de descartar la notificación
  (13+), del gestor de tareas en primer plano (13+) ni de la espera de arranque tras `BOOT_COMPLETED`
  (15+ para ciertos tipos). Detalle de qué reglas dependen del target: (sin verificar en Android 16 y 17).

## A. Servicio central

**R04.1** WHILE el host esté en ejecución y el usuario no lo haya desactivado THE host SHALL
mantener un servicio en primer plano ("servicio central") en `:black` con tipo `specialUse`, con
notificación persistente de importancia baja, texto en el idioma del sistema y toque que abre el host.
- Verificación: `adb shell dumpsys activity services <host>` muestra `isForeground=true`;
  `dumpsys notification --noredact` muestra el canal del servicio central.

**R04.2** THE servicio central SHALL declarar el tipo `specialUse` con su propiedad de
subtipo y los permisos `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_SPECIAL_USE`.
- Verificación: `dumpsys package <host>` (permisos); inspección del manifiesto fusionado en CI.

**R04.3** THE motor SHALL NOT cancelar ni ocultar la notificación del servicio central.
- Verificación: tras 5 min, la notificación sigue; revisión de código (`DaemonInnerService` eliminado o inocuo).

**R04.4** IF el arranque del servicio en primer plano falla por restricciones de segundo plano
THEN THE host SHALL registrar el motivo, reintentar al volver a primer plano y mostrar el estado en la pantalla principal.
- Verificación: forzar la excepción con `adb shell am set-standby-bucket <host> restricted` y arrancar desde un broadcast; logcat.

**R04.5** WHEN el usuario descarta la notificación (Android 13+) o detiene el servicio desde el
gestor de tareas en primer plano THE host SHALL seguir mostrando "motor detenido" en su pantalla
y SHALL volver a arrancarlo con una acción del usuario.
- Verificación: descartar la notificación y parar desde "Apps activas"; comprobar el estado.

## B. Arranque y reinicio

**R04.6** WHEN el dispositivo arranca (`BOOT_COMPLETED`) o el host se actualiza
(`MY_PACKAGE_REPLACED`) y el ajuste "iniciar con el sistema" esté activo THE host SHALL arrancar el
servicio central y relanzar los procesos de apps marcadas como "mantener vivas".
- Verificación: `adb reboot`, desbloquear una vez, `dumpsys activity services <host>` sin abrir la app.

**R04.7** THE host SHALL activar "iniciar con el sistema" por defecto al completar la exención de batería.
- Verificación: ajuste visible y activo tras la guía.

**R04.8** WHEN el sistema reinicia el servicio central (`START_STICKY`) o el proceso `:black` muere
THE motor SHALL restaurar la política de "mantener vivas" y los procesos asociados sin intervención.
- Verificación: `adb shell am kill <host>:black`; en 60 s el servicio y los `:pN` marcados están vivos.

## C. Batería, autoarranque y ROM

**R04.9** WHEN el usuario completa la bienvenida THE host SHALL comprobar
`PowerManager.isIgnoringBatteryOptimizations` y, IF no está exento THEN THE host SHALL lanzar
`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` con el paquete del host (o, si no resuelve, la lista de
`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`).
- Verificación: `dumpsys deviceidle whitelist | grep <host>`. Paso d.

**R04.10** WHILE el host no esté exento de optimización de batería THE host SHALL mostrar un aviso
permanente en la pantalla principal con el botón para repetir el flujo.
- Verificación: revocar con `dumpsys deviceidle whitelist -<host>` y reabrir.

**R04.11** WHERE la ROM sea ColorOS, HyperOS, MagicOS u OriginOS (detección por `Build.MANUFACTURER`
y `Build.BRAND`, y propiedades de ROM; sin verificar cuáles) THE host SHALL mostrar una guía paso a paso de
autoarranque, actividad en segundo plano sin restricciones y bloqueo en recientes, y SHALL intentar abrir la
pantalla de ajustes de la ROM con una lista de intents conocidos, con la ficha de la app como respaldo.
- Verificación: paso d en el OPPO (ColorOS) y en el móvil de ROM china; captura sin datos personales.

**R04.12** THE host SHALL permitir al usuario marcar cada paso de la guía como hecho, porque su estado
no es consultable por API, y SHALL conservar la marca.
- Verificación: marcar, reiniciar la app, la marca sigue.

**R04.13** IF ningún intent de ajustes de la ROM resuelve THEN THE host SHALL mostrar las instrucciones
manuales del fabricante sin fallar.
- Verificación: prueba unitaria con un `PackageManager` simulado que no resuelve nada.

## D. Procesos invitados vivos

**R04.14** WHERE una app del box esté marcada "mantener viva" (por defecto GMS y GSF; el usuario puede
añadir WhatsApp, Gmail u otras) THE motor SHALL mantener todos sus procesos con importancia no inferior a la de
un servicio (no en caché) mientras el servicio central esté activo.
- Verificación: `dumpsys activity processes <host>` muestra los `:pN` con `procState` ≤ servicio y fuera de
  `cch`; tras 60 min de pantalla apagada siguen vivos.

**R04.15** IF un proceso de una app marcada muere THEN THE motor SHALL relanzarlo en menos de 30 s,
con espera creciente (30 s, 1, 5 min) tras fallos repetidos y aviso al usuario tras 5 fallos seguidos.
- Verificación: `adb shell kill <pid>` del `:pN` de GMS; reaparece; matar 6 veces y comprobar la espera y el aviso.

**R04.16** THE host SHALL permitir activar o desactivar "mantener viva" por app, con una lista
visible y la estimación del número de procesos usados sobre `ProxyManifest.FREE_COUNT`.
- Verificación: UI; desactivar una app y comprobar que su proceso deja de reanimarse.

**R04.17** THE motor SHALL entregar los broadcasts y alarmas del invitado a procesos vivos o muertos:
WHEN el invitado programa una alarma con `PendingIntent` de broadcast THE motor SHALL dispararla en la
hora indicada dentro del box (los métodos `set*` del invitado no pueden descartarse en silencio).
- Verificación: NotifProbe, caso de alarma exacta a 10 s y a 15 min en modo Doze (`dumpsys deviceidle force-idle`).

## E. Servicios en primer plano del invitado

**R04.18** WHEN un servicio del invitado llama a `startForeground` THE motor SHALL pasar su notificación
por el mismo tratamiento de la spec 02 (canal `bb:`, iconos, `subText`) para que el canal exista en el host y
la llamada no falle por canal inválido.
- Verificación: NotifProbe con un servicio en primer plano; notificación visible y servicio vivo 5 min.

**R04.19** WHILE un servicio del invitado esté en primer plano THE motor SHALL reflejarlo en el servicio
real de proceso (`ProxyService$Pn`) para que el proceso suba de prioridad, con tipo 0 (ninguno) mientras el
`ProxyService` stub no declare tipos.
- Verificación: `dumpsys activity services <host>` muestra el `ProxyService$Pn` con `isForeground=true`.

**R04.20** WHERE el invitado necesite micrófono, cámara o ubicación en segundo plano THE motor SHALL
declararlo como fuera de alcance de esta spec y registrarlo como pendiente. Verificación: nota en el diseño.

## F. Android 14 a 17

**R04.21** THE host SHALL conservar el mismo comportamiento de servicio central en Android 13, 14, 15, 16
y 17, y SHALL documentar, por versión, qué reglas no le afectan por targetSdk 28.
- Verificación: tabla en el diseño rellenada con la salida de `dumpsys` en cada versión (sin verificar hoy).

**R04.22** WHILE el dispositivo ejecute Android 17 o posterior THE host SHALL registrar en cada arranque
`ActivityManager.getHistoricalProcessExitReasons` de sus procesos y SHALL avisar si un proceso del box fue
terminado por límite de memoria.
- Verificación: `adb shell dumpsys activity exit-info <host>` y la pantalla de estado. Riesgo R10.

**R04.23** IF la memoria disponible baja de un umbral configurable THEN THE motor SHALL NOT arrancar
procesos de apps no marcadas y SHALL ofrecer al usuario reducir la lista de apps vivas.
- Verificación: prueba con la lista completa en un dispositivo con poca RAM; sin muertes en cascada.

## G. Medición y aceptación

**R04.24** THE proyecto SHALL incluir una herramienta de medición de latencia de push (app de prueba
que recibe FCM dentro del box y script que compara las horas), sin claves ni tokens en el repositorio.
- Verificación: la herramienta publica el retardo medio y máximo de N mensajes.

**R04.25** WHEN un mensaje de WhatsApp o Gmail se envía al box con pantalla apagada tras 60 minutos
de inactividad THE box SHALL mostrar la notificación en menos de 1 minuto, en al menos 9 de 10
mensajes y sin superar 2 minutos en ninguno, en Android 16 y en 17.
- Verificación: paso d del plan de pruebas a 1, 10 y 60 min.

**R04.26** THE proyecto SHALL documentar un procedimiento de medición del consumo de batería del host
en reposo (`dumpsys batterystats`, por adb) y registrar el resultado como dato informativo, sin umbral de
aceptación hasta tener medidas.
- Verificación: informe de una hora en reposo con GMS y WhatsApp vivos, comparado con WhatsApp fuera del box.
