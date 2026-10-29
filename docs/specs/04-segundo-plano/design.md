# Spec 04: segundo plano y push (FCM). Diseño

Estado: Borrador

Rutas relativas a `engine/`. `B` = `Bcore/src/main/java/top/niunaijun/blackbox/`,
`A` = `app/src/main/java/top/niunaijun/blackboxa/`. Lo no comprobado lleva "(sin verificar)".

## 1. Modelo de procesos y de prioridad

```
 :black  (DaemonService, BProcessManagerService, ...)   ← servicio en primer plano real
 :p0 … :p49  (una app o proceso invitado cada uno)       ← sin servicio ni cliente: "en caché"
```

Android calcula la prioridad **por proceso**. Un servicio en primer plano en `:black` no
protege a `:pN`. Un `:pN` sin componentes activos queda en caché y está expuesto al
congelador de apps en caché (Android 11+, según ROM), al LMK y a los límites de la ROM
china. FCM (GMS `persistent`, que mantiene la conexión a `mtalk`) necesita un proceso
vivo, con red y sin congelar. Por eso hay tres capas:

| Capa | Qué protege | Mecanismo |
|---|---|---|
| 1. Host | UID completo frente a Doze, App Standby, red en segundo plano | Exención de batería (R04.9) y guía de ROM (R04.11) |
| 2. Servicio central | `:black` y el estado del box | Servicio en primer plano `specialUse` (R04.1) |
| 3. Procesos invitados | Cada `:pN` marcado | Conexión desde `:black` + reinicio (R04.14, R04.15) |

## 2. Estado actual (código)

| Pieza | Fichero | Observación |
|---|---|---|
| Servicio | `B core/system/DaemonService.java` | `START_STICKY`, `startForeground(NOTIFY_ID, n)` sin tipo; notificación en inglés ("BlackBox Core"); `DaemonInnerService` hace `nm.cancel(NOTIFY_ID)` y `stopSelf` |
| Arranque | `B BlackBoxCore.java` (≈ líneas 480 a 560, 870 a 950, 1920 a 2045) | Tres copias del mismo bucle de reintentos con `startForegroundService` y `startService` |
| Stub de servicios | `B proxy/ProxyService.java` | `showNotification()` privado, no se llama. No hay `startForeground` |
| Primer plano del invitado | `B fake/service/IActivityManagerProxy.java` (`setServiceForeground`) | Pone a 0 el último `Integer` de los argumentos (tipo). La notificación no se transforma |
| Alarmas | `B fake/service/IAlarmManagerProxy.java` | `set` devuelve 0 sin hacer nada; el resto pasa al sistema real con PI sin reescribir (sin verificar el resto de métodos) |
| Muerte de procesos | `B core/system/BProcessManagerService.java` (`onProcessDie`, `restartAppProcess`, `startProcessLocked`) | Hay `linkToDeath` por proceso; no hay política de reinicio |
| Manifiesto | `Bcore/src/main/AndroidManifest.xml` | `DaemonService` con `foregroundServiceType="specialUse"` y permisos `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`; `RECEIVE_BOOT_COMPLETED` sin receptor; sin `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ni propiedad de subtipo |
| Interfaz | `A view/main/WelcomeActivity.kt`, `A view/setting/SettingFragment.kt` | Punto natural para la guía y los ajustes |

## 3. Reglas de Android que afectan (y cuáles no por targetSdk 28)

La columna "target" dice si la regla depende del `targetSdk` de la app. (sin verificar) en
cada fila significa que aún no se ha comprobado en Android 16 y 17 de ROM china.

| Regla | Versión | ¿Depende del target? | Efecto en el host (target 28) |
|---|---|---|---|
| Tipo obligatorio en `startForeground` y permiso `FOREGROUND_SERVICE_<TIPO>` | 14 | Sí, solo target ≥ 34 | No se exige. Se declara `specialUse` igualmente por compatibilidad futura (R8) |
| Tiempos máximos por tipo (`dataSync`, `mediaProcessing`) | 15 | Sí, solo target ≥ 35 | No aplican. `specialUse` no tiene límite documentado (sin verificar en 16 y 17) |
| Arranque de servicio en primer plano desde segundo plano prohibido | 12 | No | Aplica. Exenciones útiles: exención de batería, `BOOT_COMPLETED`, FCM de prioridad alta, notificación tocada |
| `BOOT_COMPLETED` no puede arrancar ciertos tipos (`dataSync`, `camera`, `mediaPlayback`, `microphone`, `phoneCall`, `mediaProjection`) | 15 | No (sin verificar) | `specialUse` no está en la lista |
| Notificación de servicio en primer plano descartable | 13 | No | El usuario puede quitar la notificación sin parar el servicio |
| Gestor de tareas en primer plano ("Apps activas") | 13 | No | El usuario puede parar el servicio central (R04.5) |
| Congelador de apps en caché | 11+ según ROM | No | Afecta a `:pN` sin cliente: motivo de la capa 3 |
| Límites de memoria por app | 17 | Hipótesis | R10 de riesgos; ver §8 |
| Redirección de intents | 16 | Sí | Sin impacto hoy; vigilar |

## 4. Servicio central (R04.1 a R04.5)

- Mantener la clase `DaemonService` (menos churn) y renombrar solo el texto visible a "Motor activo".
  Cambios:
  - `startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` en API 34+ (API 29+ acepta tipo); en
    inferiores, la variante sin tipo.
  - Cadena de texto en `strings.xml` de `Bcore` con traducción al español; `PendingIntent` de contenido al
    `LauncherActivity` del host.
  - Quitar `DaemonInnerService` o dejarlo vacío (su `cancel` no tiene efecto sobre una notificación de
    primer plano y la intención es contraria a R04.3).
  - `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" .../>` en el manifiesto.
- Unificar los tres bucles de arranque de `BlackBoxCore` en `B core/system/CoreServiceLauncher.java`
  (nuevo): un único método `ensureRunning(Context)` con reintentos y registro del motivo del fallo
  (R04.4). La pantalla principal consulta `CoreServiceLauncher.isRunning()` (usa
  `ActivityManager.getRunningServices`, o una bandera en `SystemCallProvider`; sin verificar cuál es fiable
  en Android 16).
- `onTaskRemoved` del servicio: no reiniciar en bucle; el usuario manda (R04.5).

## 5. Batería y ROM (R04.9 a R04.13)

Clases nuevas en el host (`A util/PowerGuide.kt`, `A view/main/BackgroundGuideActivity.kt`):
- `isIgnoringBatteryOptimizations()`; intent `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` con
  `Uri.parse("package:" + packageName)`; permiso en el manifiesto del host.
- Tabla de intents por ROM (componente → resolución con `PackageManager.resolveActivity`; si no resuelve, ficha
  de la app `ACTION_APPLICATION_DETAILS_SETTINGS`). Los nombres de componente cambian entre versiones y
  se mantienen en un fichero `rom_intents.json` en `assets/`, para actualizarlos sin recompilar la lógica:

| ROM | Lo que hay que activar (texto de la guía) | Componentes conocidos |
|---|---|---|
| ColorOS | Permitir inicio automático; Batería > actividad en segundo plano "Sin restricciones"; bloquear en recientes | `com.coloros.safecenter/.startupapp.StartupAppListActivity` y variantes (sin verificar en la versión actual) |
| HyperOS (Xiaomi) | Inicio automático; Ahorro de batería "Sin restricciones"; bloqueo en recientes | `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity` (sin verificar) |
| MagicOS (Honor) | Inicio de aplicaciones: gestionar manualmente (3 interruptores); batería | `com.hihonor.systemmanager/...` (sin verificar) |
| OriginOS (vivo) | Inicio automático; Ejecución en segundo plano de alto consumo | `com.vivo.permissionmanager/...` (sin verificar) |

- Los pasos de la guía se guardan en `SharedPreferences` del host (marca del usuario, R04.12).
- Prueba unitaria de la tabla con un `PackageManager` falso (R04.13).

Alternativa descartada: pedir permisos de accesibilidad para pulsar los ajustes automáticamente. Es frágil y
amplía el riesgo de privacidad.

## 6. Procesos invitados vivos (R04.14 a R04.16)

### 6.1 Política

`B core/system/keepalive/KeepAliveManager.java` (nuevo, en `:black`):
- Lista de paquetes+perfiles marcados, en `BEnvironment.getSystemDir()/keepalive.json` (la carpeta
  existe, `getSystemDir`). Por defecto: GMS y GSF cuando estén instalados (spec 03).
- Se engancha a `BProcessManagerService.startProcessLocked` (alta) y `onProcessDie` (baja y reinicio).
- API AIDL: ampliar `IBProcessManagerService` con `setKeepAlive(pkg,userId,boolean)` y
  `getKeepAlive()` (cambio de AIDL; sin verificar el nombre del fichero AIDL).

### 6.2 Mecanismo de prioridad (decisión pendiente por medición, T04.1)

| Opción | Cómo | Pros | Contras |
|---|---|---|---|
| A. Bind de servicio | `:black` hace `bindService` a un `ProxyService$Pn` con una acción propia (`bb.KEEPALIVE`) y `BIND_AUTO_CREATE\|BIND_IMPORTANT`; el proceso hospedador hereda al menos la importancia del cliente | Simple, estándar, sin pérdida si `:pN` muere (el bind se rehace) | `AppServiceDispatcher.onBind` debe ignorar la acción; consume un servicio por proceso |
| B. Proveedor | `:black` mantiene un cliente inestable de `ProxyContentProvider$Pn` | Ya existe `BActivityManager.acquireContentProviderClient` | Un proveedor estable mataría a `:black` si `:pN` muere: usar solo inestable; efecto en prioridad (sin verificar) |
| C. Servicio en primer plano propio por proceso | `:pN` arranca su `ProxyService` y llama a `startForeground` | Máxima prioridad, notificación por proceso | Hasta 50 notificaciones; mucho ruido |
| D. Nada (solo capa 1 y 2) | | Cero código | `:pN` en caché: probablemente insuficiente en ROM china (sin verificar) |

Propuesta: A, con medición previa de B y de D como referencia (T04.1). Criterio de salida del spike:
*continuar con A* si `dumpsys activity processes` muestra `:pN` fuera de caché tras 60 min con pantalla
apagada; *probar B* si no; *pivotar a C* si ninguna sirve y documentar el coste en notificaciones.

### 6.3 Reinicio con espera creciente (R04.15)

En `onProcessDie(ProcessRecord)`, si el par (paquete, perfil) está marcado: programar `restartAppProcess(...)`
(ya existe, línea 119) con `Handler` del `:black` y esperas 30 s, 1 min, 5 min; contador a cero si el proceso
vive más de 10 min. Tras 5 fallos seguidos: notificación de aviso (canal del host) y parar de reintentar
hasta acción del usuario.
Reiniciar un proceso arranca el stub `:pN`, no una actividad; no hace falta
primer plano (sin verificar si el sistema real permite iniciar un proceso de segundo plano en `:black` por
`ActivityManager.startService` hacia `ProxyService` desde un servicio en primer plano: sí, el servicio central lo es).

### 6.4 Proceso de GMS y sus subprocesos

GMS usa varios procesos (`.persistent`, `.ui`, `.unstable`, `.gservice`; sin verificar los nombres en el APK actual).
Cada proceso consume un slot de `FREE_COUNT = 50`. La política marca por paquete y mantiene **todos**
sus procesos conocidos; la interfaz muestra los slots usados (R04.16).

## 7. Servicios en primer plano del invitado y alarmas (R04.17 a R04.20)

- `setServiceForeground` (en `IActivityManagerProxy`): el `Notification` del argumento pasa por
  `NotificationPresenter` de la spec 02 y se reescribe su `mChannelId` con el mapeo `bb:`
  **antes** de llegar al sistema real. Hoy el canal del invitado no existe en el host; con target 28 el
  sistema aborta con "Invalid channel for service notification" (sin verificar en Android 16).
- El tipo se deja a 0 mientras `ProxyService$Pn` no declare tipos en el manifiesto. El `ServiceRecord`
  real es el del stub, así que `startForeground` del invitado sube de verdad la prioridad del proceso
  (R04.19). Para micrófono o cámara en segundo plano habría que declarar tipos en los 50 stubs y añadir
  permisos `FOREGROUND_SERVICE_*`: queda fuera (R04.20).
- Alarmas: el trabajo de reescritura de PI de broadcast es la tarea T02.10 de la spec 02. En esta spec
  se retira el `set` vacío de `IAlarmManagerProxy` y se verifica que `set`, `setExact`,
  `setExactAndAllowWhileIdle`, `setAndAllowWhileIdle`, `setRepeating` y `setAlarmClock` llegan al sistema con
  PI del host. Los nombres de las llamadas AIDL en Android 16 y 17 están (sin verificar). Las alarmas
  exactas exigen `SCHEDULE_EXACT_ALARM` en target ≥ 31; con target 28 está concedido (sin verificar).

## 8. Android 17 y memoria (R04.22, R04.23)

- Hipótesis de R10: el límite por app de Android 17 podría agregar todos los procesos del UID. Se
  mide: al arrancar, registrar `ActivityManager.getHistoricalProcessExitReasons(null, 0, 0)` y guardar
  razón, hora y proceso en un fichero rotado del host; mostrar el último en la pantalla de estado.
- Presupuesto: con `ActivityManager.getMemoryInfo` por debajo del umbral, `KeepAliveManager` no arranca
  procesos no marcados y propone reducir la lista. Umbral inicial: `availMem` < 15 % de `totalMem`
  (parámetro; el valor sale de las pruebas, no de la teoría).
- `A util/MemoryManager.kt` ya existe y mide el heap de la JVM del host: no sirve para esto, no se reutiliza.

## 9. Arranque (R04.6 a R04.8)

- Receptor nuevo `B core/system/BootReceiver.java` (exportado, con `BOOT_COMPLETED` y
  `MY_PACKAGE_REPLACED`) que llama a `CoreServiceLauncher.ensureRunning` si el ajuste lo permite.
- Android 12+ permite arrancar un servicio en primer plano desde `BOOT_COMPLETED`; en 15 hay lista de tipos
  excluidos, `specialUse` no está (sin verificar).
- Si el usuario ha forzado la detención desde Ajustes, el sistema no entrega `BOOT_COMPLETED`: documentarlo
  en la pantalla de estado.
- `LOCKED_BOOT_COMPLETED` se descarta: el almacenamiento del box está en el almacenamiento cifrado por
  credencial (sin verificar).

## 10. Medición (R04.24 a R04.26)

Herramienta nueva `tools/pushprobe/` (como NotifProbe, sin Gradle): app mínima con la biblioteca de
cliente de FCM que se registra dentro del box y publica una notificación con el retardo entre la hora de
envío (campo `data.sent_at`) y la de recepción. Requiere un proyecto de mensajería propio del propietario: la
configuración y el token **no entran en el repositorio** (`.gitignore` y plantilla `*.example`). Dependencia de la
spec 03. Para la prueba con WhatsApp y Gmail: el emisor anota la hora y se compara con el campo `when` de
`dumpsys notification --noredact`. Procedimiento de reposo: pantalla apagada, `adb` inalámbrico desconectado
(adb mantiene el dispositivo despierto), cargador fuera.

## 11. Pantalla de estado (soporte de todo lo anterior)

`A view/main/` nueva `StatusFragment`: servicio central activo, exención de batería, pasos de ROM, lista
"mantener vivas" con procesos y slots, última razón de salida de procesos, "iniciar con el sistema".

## 12. Riesgos

| Riesgo | Mitigación |
|---|---|
| El bind (opción A) no basta en ROM china | Medición previa (T04.1); fallback C |
| La ROM mata `:black` o los `:pN` pese a todo ("limpiar" de la ROM) | Guía de ROM, estado visible y registro de razones de salida |
| Las 50 ranuras se agotan con GMS y varias apps | Contador visible y límite de apps vivas (R04.16, R04.23) |
| Intents de ajustes de la ROM cambian | `rom_intents.json` en `assets/` y respaldo a la ficha de la app |
| `targetSdk` sube (R8) y exige tipos y permisos | Se declaran ya `specialUse` y sus permisos; revisión en el ADR de targetSdk |
| Consumo de batería alto | R04.26 como dato y ajuste para desactivar el servicio central |
| FCM no llega por razones ajenas (GMS roto) | La spec depende de 03; PushProbe separa fallos de conexión de fallos de entrega |

## 13. Plan de pruebas

- Unitarias: tabla de intents por ROM, política de espera de reinicio.
- Dispositivo: `dumpsys deviceidle whitelist`, `dumpsys activity services/processes <host>`,
  `dumpsys activity exit-info <host>`, `adb shell am kill`, `adb reboot`.
- Doze: `dumpsys deviceidle force-idle` y `unforce`.
- Aceptación: paso d a 1, 10 y 60 minutos, en Android 16 y 17, con PushProbe y con WhatsApp y Gmail.
