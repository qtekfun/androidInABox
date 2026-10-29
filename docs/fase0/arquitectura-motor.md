# Fase 0: cómo funciona el motor de NewBlackbox

Lectura del código de `Bcore` en el commit `89b5983`. Las rutas son relativas a
`Bcore/src/main/java/top/niunaijun/blackbox/` salvo que se indique otra cosa. Lo que no
está verificado se marca como **hipótesis**.

## 1. Visión general

```
           App demo (top.niunaijun.blackbox)            ← UI, proceso principal
                        │
   ┌────────────────────┴─────────────────────┐
   │ Proceso servidor  ":black"               │  "sistema" virtual: BPackageManagerService,
   │ (SystemCallProvider)                     │  BActivityManagerService, BProcessManagerService,
   │                                          │  BNotificationManagerService, BAccountManagerService,
   │                                          │  BUserManagerService, BJobManagerService...
   └────────────────────┬─────────────────────┘
                        │ binder (AIDL propio, IB*Service)
   ┌──────────┬─────────┴─┬──────────┐
   │ :p0      │ :p1       │ … :p49   │  procesos stub: cada uno ejecuta una app invitada
   │ (Proxy*) │           │          │  con hooks Java + nativos inyectados
   └──────────┴───────────┴──────────┘
```

- Todo corre con **un solo UID real**, el del host. El aislamiento entre apps invitadas
  y perfiles ("User N") es lógico: UIDs virtuales y directorios separados. No es un
  aislamiento del kernel.
- El manifiesto del host declara unos 300 componentes stub repartidos en 50 procesos:
  - 150 actividades: `ProxyActivity$P0..P49`, `TransparentProxyActivity$Pn` y
    `ProxyPendingActivity$Pn`.
  - 50 `ProxyService$Pn` y 50 `ProxyJobService$Pn`.
  - 50 `ProxyContentProvider$Pn`.
  - Un `ProxyBroadcastReceiver`.
- `targetSdkVersion` del host: **28**. Por eso el sistema le aplica el comportamiento
  heredado: no exige tipo en los servicios en primer plano ni flags de export en los
  receivers, y es más laxo con APIs ocultas. Ver
  [riesgos-android.md](riesgos-android.md).

## 2. Proxies de servicios del sistema

`fake/hook/HookManager.java` registra unos 80 inyectores en los procesos stub y en el
servidor. Hay dos mecanismos:

- **`BinderInvocationStub`**:
  - Envuelve el `IBinder` real y sustituye la entrada de `ServiceManager.sCache`
    (`replaceSystemService`).
  - Además parchea el singleton cacheado del manager. Por ejemplo
    `ActivityThread.sPermissionManager` y `AppOpsManager.mService`.
- **`ClassInvocationStub`**: crea un `java.lang.reflect.Proxy` sobre el objeto real y
  lo coloca en el singleton. Lo usan:
  - `ActivityManager.IActivityManagerSingleton`
  - `ActivityClient.INTERFACE_SINGLETON`

  ActivityTaskManager es un `BinderInvocationStub`: escribe un proxy de binder en
  `IActivityTaskManagerSingleton`.

Cada método interceptado es una clase `@ProxyMethod("nombre")`. La mayoría reescribe el
nombre de paquete del invitado por el del host (`MethodParameterUtils.replaceAllAppPkg`)
y redirige la llamada al servicio virtual equivalente del proceso `:black`.

Servicios con proxy, entre otros:
- **Actividad y paquetes:** ActivityManager, ActivityTaskManager, ActivityClient,
  PackageManager, PermissionManager, AppOps, Alarm, JobScheduler.
- **Contenido y notificaciones:** Notification, ContentProvider, Settings, Account.
- **Sistema:** Location, Telephony, Wifi, Connectivity, Storage, User, Display,
  Window, Power, Vibrator, Audio, MediaSession, Shortcut, LauncherApps, Autofill,
  Accessibility, DevicePolicy.
- **Específicos de fabricante:** Xiaomi/MIUI.

`HookManager.checkEnv` vuelve a inyectar un proxy si detecta que otro código lo ha
desmontado (`isBadEnv`).

Las APIs ocultas se desbloquean así:
1. Primero, `NativeCore.disableHiddenApi()` (`cpp/hidden_api.cpp`). Busca en
   `libart.so` el símbolo `VMRuntime::setHiddenApiExemptions` y lo llama con `"L"`.
2. Si falla, recurre a `me.weishu.reflection.Reflection.unseal` (FreeReflection 3.2.2).
3. **En Android 16 el paso 1 falla:** `HiddenAPI: Didn't find setHiddenApiExemptions in
   any form`. Ver [resultados-dispositivo.md](resultados-dispositivo.md).

## 3. Redirección de IO

No se hace con hooks de libc:
- `cpp/Hook/FileSystemHook.cpp` y `cpp/Utils/AntiDetection.cpp` resuelven `open`/`stat`
  pero nunca llaman a `DobbyHook`. Es código muerto.

La redirección real tiene dos capas:
1. **Hooks JNI de `java.io.UnixFileSystem`** (`cpp/Hook/UnixFileSystemHook.cpp`):
   - Métodos enganchados: `canonicalize0`, `list0`, `createDirectory0`,
     `createFileExclusively0`, `getLastModifiedTime0`, `setPermission0`, `getSpace0`.
   - El reemplazo se hace sobrescribiendo el puntero JNI del `ArtMethod`
     (`cpp/JniHook/JniHook.cpp`).
   - Los offsets dentro de `ArtMethod` se calculan en tiempo de ejecución escaneando
     dos métodos nativos señuelo (`JniHook.nativeOffset`, `nativeOffset2`).
2. **`OsStub`** (proxy de `libcore.io.Os`): reescribe todo argumento `String` que
   empiece por `/` mediante `IOCore.redirectPath`.

Reglas (`core/IOCore.java`, `enableRedirect`):
- `/data/data/<pkg>` y `/data/user/<n>/<pkg>` van al `dataDir` virtual, dentro de
  `<dataDir del host>/blackbox/data/user/<userId>/<pkg>` (`core/env/BEnvironment.java`).
- `/sdcard` y `/storage/emulated/<n>` van a un directorio externo por usuario virtual.
- `/proc/self/cmdline` va a un fichero falso por proceso.
- Con "Hide Root", las rutas `su` se ocultan.

**Limitación:** el código nativo de las apps invitadas que llama a `open()` directamente
**no** se redirige. Funciona en la práctica porque el `ApplicationInfo` del invitado ya
apunta a los directorios virtuales.

## 4. Gestión de procesos

El ciclo de vida de un proceso invitado:
1. `core/system/BProcessManagerService.java` asigna al invitado el primer stub libre
   `:pN`. No hay un mapeo fijo paquete → stub.
2. Arranca el proceso tocando `ProxyContentProvider$Pn.call("_Black_|_init_process_")`.
3. Ese provider llama a `BActivityThread.initProcess`.
4. `BActivityThread.handleBindApplication` construye el contexto del invitado:
   - `createPackageContext(guestPkg, CONTEXT_INCLUDE_CODE | CONTEXT_IGNORE_SECURITY)`.
   - Parchea `LoadedApk.mApplicationInfo`.
   - Ajusta `VMRuntime.setTargetSdkVersion` al targetSdk del invitado.
   - Activa la IO.
   - Llama al `LoadedApk.makeApplication` real.
5. Los UIDs virtuales salen de `pm/Settings.acquireAndRegisterNewAppIdLPw`, con
   `FIRST_APPLICATION_UID + n` y `PER_USER_RANGE = 100000`.

**Actividades:**
1. El invitado llama a `startActivity`. El proxy de ActivityManager la convierte en un
   intent hacia `ProxyActivity$Pn`, con el destino real guardado en extras
   (`ProxyActivityRecord`).
2. `HCallbackProxy` sustituye `ActivityThread.mH.mCallback`.
3. En `EXECUTE_TRANSACTION`, `HCallbackProxy` busca el `LaunchActivityItem` dentro de
   `ClientTransaction.mActivityCallbacks` y reescribe su `mIntent` e `mInfo` con los
   del invitado.

**Servicios:**
- Se lanzan en `ProxyService$Pn` y el `AppServiceDispatcher` los reenvía a la clase del
  invitado.
- `startForeground` del invitado pasa por `setServiceForeground` con el tipo forzado a 0.
- Los stubs no declaran `foregroundServiceType`.

**Receivers:**
- Los estáticos del invitado se registran dinámicamente en el host
  (`am/BroadcastManager`) con `RECEIVER_EXPORTED`.
- Los dinámicos se interceptan. Desde API 33 se fuerza `RECEIVER_NOT_EXPORTED` si el
  invitado no indicó ningún flag.

**Instalación de APK** (`pm/BPackageManagerService.installPackageAsUserLocked`):
1. Copia el APK.
2. Lo valida con `getPackageArchiveInfo`.
3. Lo analiza con `android.content.pm.PackageParser`, que es oculto pero sigue presente
   en Android 16 y en `main`.

**Sin soporte de split APK:**
- `installer/CopyExecutor` copia solo el APK base.
- `PackageManagerCompat.generateApplicationInfo` no rellena `splitSourceDirs` ni
  `splitNames`.
- En los `PackageInfo` de respaldo, `splitNames` se fuerza a vacío
  (`fake/frameworks/BPackageManager.java:681, 830`).

## 5. Hooks nativos

Los instala `cpp/BoxCore.cpp` → `nativeHook()`:

| Hook | Método | Propósito |
|---|---|---|
| `UnixFileSystemHook` | JNI `java.io.UnixFileSystem.*` | Redirección de rutas |
| `VMClassLoaderHook` | `VMClassLoader.findLoadedClass` | Ocultar clases de Xposed/Epic |
| `BinderHook` | `Binder.getCallingUid` | Devolver UIDs virtuales |
| `DexFileHook` (API ≥ 34) | `DexFile.openDexFileNative` | `chmod` a solo lectura de los dex propios, porque Android 14 rechaza dex escribibles |
| `VirtualSpoof` (Dobby inline) | libc `__system_property_get` | Hacer pasar **todo proceso que cargue `libblackbox.so`** (invitados, host y servidor) por un Pixel 6 con Android 12 (build SP1A.210812.015, parche 2022-01-05). Se instala en un constructor de la biblioteca |

`RuntimeHook` existe pero no se compila. xDL va incluido y Dobby se enlaza como `.a`
precompilado.

## 6. Notificaciones de las apps invitadas

Flujo (`fake/service/INotificationManagerProxy.java` →
`fake/frameworks/BNotificationManager.java` →
`core/system/notification/BNotificationManagerService.java`):

1. El invitado llama a `NotificationManager.notify(tag, id, n)`. El proxy intercepta
   `enqueueNotificationWithTag` y envía el `Notification` por binder al proceso `:black`.
2. `BNotificationManagerService.enqueueNotificationWithTag`:
   - Cambia el canal a `<canal>@black-<userId>`, y el grupo de forma análoga.
   - Calcula el id real como `hash(paquete + userId + id)`.
   - Publica con el `NotificationManager` **del host**:
     `mRealNotificationManager.notify(notificationId, notification)`.
3. Los canales se crean en el host con el id reescrito. La tabla
   canal → paquete vive solo en memoria (`NotificationRecord`).
4. PendingIntents (`IActivityManagerProxy.GetIntentSender`):
   - Los de tipo **actividad** se reescriben hacia `ProxyPendingActivity$Pn`.
   - Los de tipo **broadcast y servicio no se tocan**: siguen apuntando al componente
     del paquete invitado.

Consecuencias medidas en un dispositivo real
([resultados-dispositivo.md](resultados-dispositivo.md)):

- La notificación aparece **a nombre del host** ("BlackBox"). No hay nada que indique
  la app invitada ni el perfil.
- **El `tag` se ignora:** dos `notify(tag, id)` con tags distintos se pisan.
- El canal no incluye el paquete. Dos apps del mismo perfil con el mismo id de canal
  (por ejemplo `default` o `miscellaneous`) comparten canal real.
- El icono pequeño es un `Icon` de recurso del invitado, resuelto contra el APK del host.
  Sale un icono equivocado: en la prueba, por casualidad, el `ic_launcher` del host.
- Tocar la notificación funciona. **Responder no**: tanto las acciones por broadcast como
  las de servicio se pierden.
- Si la app invitada también está instalada fuera del box, la acción iría a la app
  **real**. Hay riesgo de cruzar datos entre el perfil virtual y el real.

## 7. GMS y cuentas

- `core/GmsCore.java`:
  - `installGApps` solo **clona paquetes de Google ya instalados en el host**, con
    `installPackageAsUser(nombrePaquete)`.
  - `isSupportGms()` devuelve true solo si el host tiene `com.google.android.gms`.
  - En una ROM sin GMS, el gestor de GMS no tiene nada que instalar.
- No hay suplantación de firmas, así que microG no puede hacerse pasar por GMS sin
  parchear el motor.
- Los content providers `com.google.android.gms*`, `gsf` y `vending` se dejan pasar al
  **sistema real** (`IActivityManagerProxy`, `getContentProvider`). En un dispositivo
  con GMS, el GMS real los rechaza (`Permission Denial ... INTERNAL_BROADCAST`). En uno
  sin GMS no existen.
- Cuentas:
  - `BAccountManagerService` es un `AccountManager` virtual completo: carga los
    autenticadores de los paquetes invitados.
  - `GoogleAccountManagerProxy` y `GmsProxy` parecen código muerto (**hipótesis**):
    `inject()` está vacío o el servicio `"gms"` no existe.
- `IPackageManagerProxy` fabrica un `PackageInfo` falso de `com.android.vending` con
  versión fija y sin firmas.

## 8. Telemetría incluida en el upstream

`ClientConfiguration.getLogSenderChatId()` devuelve un id de chat de Telegram
**fijo en el código**. `SimpleCrashFix` llama a `BlackBoxCore.sendLogs` ante cualquier
excepción capturada o fatal. `sendLogs` hace lo siguiente:
1. Ejecuta `logcat -d -v threadtime`, que con el UID del host incluye los logs de
   **todas las apps invitadas**.
2. Le añade datos del dispositivo.
3. Lo envía con POST a `https://logs-sender-api.vercel.app/api/<chatId>/upload`
   (`utils/LogSender.java`).

El envío está **activo por defecto**. Para un producto de privacidad es inaceptable:
hay que eliminarlo en el fork (ver ADR-0003).
