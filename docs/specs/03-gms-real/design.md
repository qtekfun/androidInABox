# Spec 03: GMS real dentro del box. Diseño

Estado: Borrador
Fecha: 2026-10-29
Requisitos: [requirements.md](requirements.md). Rutas relativas a
`engine/Bcore/src/main/java/top/niunaijun/blackbox/`.

## 1. Visión general

GMS real es un conjunto de apps que Android espera como apps de sistema privilegiadas,
con proceso persistente y UID compartido. El box las ejecuta como apps virtuales, así que
hay que lograr cuatro cosas: (1) instalarlas, (2) hacer que el `PackageManager` virtual
las presente como de sistema y con permisos, (3) enrutar providers y servicios al GMS
virtual y (4) mantenerlas vivas. Casi todo es "mentir al proceso GMS" en el
`PackageManager`/`ActivityManager` virtuales, que el motor ya controla.

Qué NO se puede emular dentro de un proceso sin root (hipótesis, a medir en el spike):
llamadas del GMS a servicios del sistema real que comprueban el UID real (p. ej. el
UID es el del host, no el de GMS). Si GMS depende de ello, el resultado es el
"pivotar".

## 2. Piezas afectadas

| Clase / fichero | Cambio |
|---|---|
| `core/GmsCore.java` | Nuevo modo "desde ficheros": `installFromFiles(gsf, gms, vending, userId)` en orden fijo; `isGoogleAppOrService` pasa a ser fuente de verdad del tratamiento privilegiado; `isSupportGms()` (host) deja de bloquear la ruta |
| `core/system/pm/BPackageManagerService.java` | `installPackageAsUserLocked` (~648-720): aceptar `InstallOption` con `FLAG_PRIVILEGED_VIRTUAL` (nuevo) para los tres paquetes; línea ~616 ya excluye GMS de ciertas listas |
| `core/system/pm/PackageManagerCompat.java` | `generateApplicationInfo` (~280): añadir `ai.flags \|= FLAG_SYSTEM \| FLAG_UPDATED_SYSTEM_APP` y el privado `PRIVATE_FLAG_PRIVILEGED` por reflexión (`ApplicationInfo.privateFlags`) para paquetes Google; `generatePackageInfo` (~56-215) rellena `requestedPermissionsFlags` con `REQUESTED_PERMISSION_GRANTED` |
| `entity/pm/InstallOption.java` | Nuevo flag `FLAG_PRIVILEGED_VIRTUAL`, distinto de `FLAG_SYSTEM` (que significa "copiar del host sin extraer libs", ver `CopyExecutor`) |
| `core/env/AppSystemEnv.java` | Lista de paquetes de sistema virtuales; añadir los de Google que haga falta |
| (nuevo) `core/gms/GooglePrivappPermissions.java` | Tabla en código (generada en T03.4 desde los XML de MindTheGapps; no se leen de la partición) con permisos privapp y default-permissions por paquete |
| `fake/service/IPackageManagerProxy.java` | `checkPermission` (~411), `checkSelfPermission` (~441): GRANTED para la tabla; `getPackageInfo` (~129-180): **eliminar** el `PackageInfo` falso de `com.android.vending` cuando Play Store está instalada en el box; `resolveService`/`getServiceInfo`/`getProviderInfo` ya consultan el PM virtual |
| `fake/service/IActivityManagerProxy.java` | `GetContentProvider` (~147-160): decidir el passthrough (ver §4); `checkPermission` (~776) para llamadas entre paquetes Google |
| `fake/delegate/ContentProviderDelegate.java` | `update(...)` solo en passthrough |
| `core/system/accounts/*` (`BAccountManagerService`, `BUserAccounts`, `RegisteredServicesParser`) | Cargar el autenticador de `com.google` desde GmsCore/GSF virtuales; aislamiento de cuentas |
| `fake/service/GoogleAccountManagerProxy.java` | Hoy devuelve cuentas reales o *mock* (`createMockGoogleAccounts`): **retirar/desactivar** con GMS virtual (devolvería cuentas falsas o reales; viola R03.18) |
| `fake/service/GmsProxy.java` | Hipótesis: código muerto (`"gms"` no existe como servicio). Confirmar y retirar |
| `utils/SimpleCrashFix.java` | `isGooglePlayServicesCrash` hace `return` y traga excepciones de GMS y de `com.google.android.gms` en cualquier app: desactivar para el spike (R03.17) |
| `app/BActivityThread.java` (~215-310) | Ramas que *saltan* servicios `google.android.gms`/`location` que no cargan: ocultan fallos reales. Desactivar para el spike |
| `core/system/BProcessManagerService.java`, `core/system/DaemonService.java` | Proceso persistente y reinicio de GMS (con spec 04) |
| `cpp/Utils/VirtualSpoof.cpp` | Propiedades falsas (Pixel 6 / Android 12): candidato de causa del crash; ver §5 |

## 3. Instalación desde ficheros

Flujo (R03.5-R03.8):

1. La UI del host pide los tres ficheros (selector de documentos; nunca descarga).
2. Verificar firma con `PackageManager.getPackageArchiveInfo(path, GET_SIGNING_CERTIFICATES)`
   frente al SHA-256 del certificado de Google (constante en código; la huella SHA-1
   `38:91:8A:45:...` está en [fuentes-gms](../../investigacion/fuentes-gms.md)). Si no
   coincide, abortar.
3. Instalar con `BlackBoxCore.installPackageAsUser(File, userId)` con
   `FLAG_PRIVILEGED_VIRTUAL`, en orden GSF, GMS, Play Store (GMS puede resolver
   `INTERNAL_BROADCAST` definido por GmsCore; GSF define permisos que GMS solicita).
   El orden se confirma en el spike (sin verificar que sea necesario).
4. Las libs nativas se extraen como en una app normal; GMS es un APK único de ~190-220 MB
   y arm64. El box copia el APK (espacio y tiempo: ~1,2 GB entre los tres).
5. Android 17: GmsCore viene en un APEX. El usuario extrae el APK (guía en
   `docs/investigacion`); el motor instala ese APK como APK normal. Riesgo: ese APK
   puede depender de recursos del APEX o de `uses-library` del sistema (sin verificar).

Los APK con `isSplitRequired` (actualizaciones de Play) no se aceptan hasta que la spec
01 cierre; en el spike se usa solo APK de ROM/MindTheGapps.

## 4. Providers: passthrough, decisión

`GetContentProvider` hoy envía a GMS/vending/gsf, `settings`, `media`, `telephony`
y los dos *launchers* chinos al sistema real. Decisión de diseño:

- **Con GMS virtual instalado en ese userId:** las authorities cuyo paquete propietario
  es un paquete Google *virtual* se resuelven con `resolveContentProvider` contra el PM
  virtual. `settings`, `media` y `telephony` siguen al sistema real.
- **Sin GMS virtual:** se mantiene el comportamiento actual (clonado desde host con GMS;
  en sistema sin GMS no hay nada que resolver). No se borra la rama: la ruta OPPO
  existe y funciona en parte (R03.14).
- Criterio: no por "modo global", sino por *instalado en este userId*; así dos usuarios
  del box pueden tener comportamientos distintos sin mezclar.
- Implementación: sustituir la condición `contains("com.google.android.gms")` por
  `GmsCore.isVirtualAuthority(auth, userId)`, que consulta el PM virtual.
- Revisar `getContentProviderExternal` y `ContentResolver.acquireProvider` y `bindService`,
  `startService` (`IActivityManagerProxy`) con la misma regla.

Alternativa descartada: dejar siempre el passthrough y arreglar `INTERNAL_BROADCAST` en
el sistema real. Imposible sin privilegios en el sistema real, y en ROM sin GMS no existe.

## 5. Causa raíz del crash de cronet (R03.15)

Síntoma: `FATAL:external/cronet/stable/base/android/jni_android.cc:220 Uncaught Java
exception in native code` (SIGTRAP). Es una excepción Java dentro de código JNI de
Cronet; el nativo la convierte en abort. La excepción original es lo que hay que ver.

Método:
1. Desactivar `SimpleCrashFix` y las ramas de salto de `BActivityThread`.
2. Capturar logcat con `-b crash,main` justo antes del SIGTRAP: buscar la excepción
   Java (suele estar unas líneas antes).
3. Contrastar con hipótesis:

| Hipótesis | Cómo se comprueba |
|---|---|
| H1 API oculta bloqueada (el bypass nativo falla, `Didn't find setHiddenApiExemptions`) | `hiddenapi: ... denied` en logcat; aplicar `HiddenApiBypass` (spec 05) antes del lanzamiento |
| H2 `.so` ausente o ABI | `UnsatisfiedLinkError`; revisar `nativeLibraryDir` y `IOCore` que redirige `/data/data/<pkg>/lib` |
| H3 proveedor de WebView/cronet (`WebViewFactory`/`TrichromeLibrary`) | Excepción desde `WebViewFactory`; probar con WebView real del sistema; está en `AppSystemEnv` |
| H4 Propiedades falsas (VirtualSpoof) | Repetir con VirtualSpoof desactivado para el proceso GMS |
| H5 Splits `*_installtime` ausentes (Cronet, Dynamite) en el GMS clonado | En APK único no aplica; explica el crash del clon |
| H6 `Context`/`AttributionSource` incoherente | `Slog` de `isAttributionSourceCrash` |

H5 podría ser la causa del clon medido: GMS clonado del OPPO tiene 13 APK y solo se
copió el base. Entonces el APK único de ROM no crashearía por esto. Si el crash persiste
con APK único, H1-H4.

## 6. Cuentas, login y DroidGuard (R03.18-R03.19)

- `BAccountManagerService` ya es virtual. GmsCore y GSF declaran un
  `AccountAuthenticator` para `com.google`; `RegisteredServicesParser` debe encontrarlo
  en los paquetes virtuales. Hay que revisar que `addAccountExplicitly`/`getAuthToken`
  llegan a `GoogleLoginService` (GSF/GMS) y no al sistema real.
- `GoogleAccountManagerProxy` (cuentas *mock* o reales) se desactiva con GMS virtual.
- Login: GmsCore abre `AccountSignInActivity`/WebView. Necesita DroidGuard (módulo
  descargado dinámicamente, `DG` en `com.google.android.gms:.droidguard`) y la
  verificación de dispositivo; puede fallar si el proceso no tiene el módulo. Sin
  verificar cómo se comporta con propiedades falsas.
- GSF/checkin: `GservicesProvider` y la base `gservices` se crean en el box; el
  `android_id` GSF resultante es propio (R03.19). Riesgo: VirtualSpoof puede cambiar
  `Build.FINGERPRINT` en el checkin, algo que Google registra (sin verificar el efecto).
- Aislamiento: ninguna llamada a `AccountManager` del box debe llegar al sistema real
  (el proxy es del box; auditar `AccountManagerCompat` y tokens en `TokenCache`).

## 7. Push (R03.21) y proceso persistente (R03.16)

Camino FCM: la app invitada llama `FirebaseMessaging.getToken()` → servicio de GMS
virtual (`c2dm`/`gcm`) → red a Google → mensaje llega al proceso de GMS (mantenido por una
conexión MCS persistente) → GMS lanza `com.google.android.c2dm.intent.RECEIVE` a la app
invitada. Las pruebas esperadas:
- El broadcast `RECEIVE` se envía dentro del box por `BroadcastManager`.
- La conexión MCS necesita proceso vivo y sin restricciones de batería (spec 04,
  R9 del veredicto).
- GMS usa `JobScheduler` y `AlarmManager`; `IJobServiceProxy` y alarmas deben soportar
  `PendingIntent` de broadcast/servicio (spec 02, R3).

GMS tiene muchos componentes (`persistent`, múltiples procesos: `gms`, `gms.persistent`,
`gms.unstable`, `gapps`). El box asigna slots `:pN` limitados: contar los procesos
que GMS pide (sin verificar; riesgo de agotar slots).

## 8. Coexistencia con GMS real del sistema (R03.22)

| Caso | Comportamiento |
|---|---|
| Sistema sin GMS (objetivo) | Solo GMS virtual; si no está instalado, las apps ven "sin GMS" |
| Sistema con GMS y box sin GMS virtual | Ruta actual (clonado/passthrough) |
| Sistema con GMS y box con GMS virtual | Authorities y servicios de Google van al GMS virtual de ese userId; el sistema real queda intacto |

Riesgo: el mismo paquete `com.google.android.gms` existe fuera y dentro; las apps
invitadas que consulten por `getPackageInfo` obtienen el del box. Datos: directorios
separados (`BEnvironment.getDataDir`), sin mezcla.

## 9. Riesgos (factuales)

- Google puede detectar un entorno virtual por UID compartido, procesos y propiedades;
  el efecto puede ser verificación adicional, restricción o bloqueo de la cuenta
  (sin verificar frecuencia; no hay política pública específica para este caso).
- Las condiciones del servicio de Google restringen el uso de sus apps fuera de
  dispositivos compatibles; instalar GApps de ROM en dispositivos sin licencia es una
  zona gris. El propietario asume el riesgo; se muestra aviso (R03.23).
- GMS se actualiza solo y puede dejar de funcionar con el motor: fijar versión (la
  de la ROM) y desactivar la actualización de GMS desde Play Store del box.
- Compatibilidad: GMS usa APIs ocultas y privilegiadas que cambian cada Android.
- Espacio: ~1,2 GB para los tres, más datos de GMS.
- Si GMS exige permisos que dependen del UID real (privileged phone state), el pivote a
  microG es la salida.

## 10. Plan de verificación

| Hito | Prueba |
|---|---|
| Instalar y arrancar | Plan de pruebas b2: 60 s sin crash, proceso vivo 2 min |
| Crash cronet | `adb logcat -d \| grep -E "jni_android.cc\|has died"` vacío 10 min |
| Providers | `grep "Permission Denial: opening provider"` vacío |
| Cuenta | Plan c; `dumpsys account` del sistema real sin la cuenta |
| Push | Plan d con NotifProbe + WhatsApp, 1/10/60 min |
| Play Store | Instalar una app de prueba |
| Regresión OPPO | Plan b1 (clonado) igual que antes |

Salida del spike: tabla de hitos con evidencias en `spike-informe.md`.

## 11. Alternativas

1. **microG** (ADR-0004: respaldo): spoof de firma en el PM virtual; 8-12 días, push
   probable, pero sin Play Store real ni Play Integrity.
2. **GMS clonado del host** (existente): descartado como ruta principal, no existe
   en ROM sin GMS.
3. **Parchear GMS (reempaquetar)**: rompe la firma, descartado.
