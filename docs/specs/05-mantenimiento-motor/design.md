# Spec 05: mantenimiento del motor — diseño

Estado: Borrador

Rutas relativas a `engine/`. `B/` = `Bcore/src/main/java/top/niunaijun/blackbox/`;
`C/` = `Bcore/src/main/cpp/`. Lo no comprobado se marca "(sin verificar)".

## 1. APIs ocultas (R05.2 a R05.4)

**Hoy.** `B/BlackBoxCore.setEssentialProperties` llama a `NativeCore.disableHiddenApi()` (nativo en
`C/BoxCore.cpp` → `disable_hidden_api` en `C/hidden_api.cpp`, que busca `VMRuntime::setHiddenApiExemptions` en
`libart.so`). Si devuelve `false`, llama a `me.weishu.reflection.Reflection.unseal(context)` (FreeReflection 3.2.2,
dependencia en `Bcore/build.gradle`). Medido en Android 16: el paso nativo falla.

**Cambio.**
- Añadir `implementation "org.lsposed.hiddenapibypass:hiddenapibypass:$hiddenApiBypass"` en `Bcore/build.gradle`
  (la variable `hiddenApiBypass = '4.3'` ya existe en el `build.gradle` raíz y no se usa).
- Nueva clase `B/utils/compat/HiddenApiGate` con `static boolean unlock()`: si `Build.VERSION.SDK_INT >= 28`
  llama a `HiddenApiBypass.setHiddenApiExemptions("L")` (la documentación actual de la biblioteca marca
  `addHiddenApiExemptions` como obsoleta porque `VMRuntime.setHiddenApiExemptions` no admite varias llamadas;
  nombre exacto y firma en la 4.3, sin verificar) y devuelve el resultado; captura `Throwable`.
- `setEssentialProperties` pasa a llamar a `HiddenApiGate.unlock()`; si da `false`, `Slog.w` con
  `Build.MODEL` y `Build.VERSION.RELEASE` (R05.3). No hay segundo mecanismo.
- Retirar FreeReflection y `NativeCore.disableHiddenApi` / `disableHiddenApi` en `C/BoxCore.cpp`, sin tocar
  `disableResourceLoading` (comparte `hidden_api.cpp`). Hacerlo en un commit aparte, tras verificar en 15, 16 y 17.

**Alternativas.** (a) Mantener el bypass nativo y arreglarlo: es el mecanismo que ya falló sin causa conocida y
depende de símbolos de `libart.so`; descartado. (b) Confiar solo en el `targetSdk` 28: es frágil ante R8 (si
algún día sube). (c) Mantener FreeReflection como respaldo: biblioteca sin mantenimiento; descartado.

**Riesgos.** El bypass actúa en cada proceso: se llama desde el mismo punto que hoy, que se ejecuta en todos los
procesos (`BlackBoxCore.doCreate`, sin verificar que cubra `:pN` antes de cualquier uso de reflexión; comprobar en T05.2).
La exención `"L"` también alcanza a la app invitada (igual que hoy).

## 2. ClientTransaction en Android 17 (R05.5 a R05.7)

**Hoy.** `B/fake/service/HCallbackProxy.getLaunchActivityItem` lee `BRClientTransaction.mActivityCallbacks()`
(interfaz de reflexión `black/android/app/servertransaction/ClientTransaction.java`, `@BField mActivityCallbacks`)
y compara el nombre de clase con `BRLaunchActivityItem.getRealClass()`. En AOSP `android16-release` y `main`
(consultado el 29-10-2026) el campo existe pero está `@Deprecated` con `maxTargetSdk = UPSIDE_DOWN_CAKE` y se rellena
"por compatibilidad"; el diseño nuevo guarda los items en `mTransactionItems` (R6 de riesgos).

**Cambio.**
- Ampliar `ClientTransaction.java` (reflexión) con `@BField List<Object> mTransactionItems()` y
  `@BMethod List<Object> getTransactionItems()` (anotaciones del módulo `black-reflection`; comprobar que el
  generador tolera miembros ausentes, es lo que `_check_*` hace en otras interfaces).
- Nueva clase `B/fake/service/LaunchItemLocator` con `static Object find(Object clientTransaction)`:
  1. `getTransactionItems()` si existe;
  2. si no, `mTransactionItems`;
  3. si no, `mActivityCallbacks`.
  Cada paso filtra los elementos por `BRLaunchActivityItem.getRealClass().isInstance(obj)` en lugar de comparar
  `getCanonicalName()` con el nombre real (más robusto con subclases; sin verificar que `getRealClass` resuelva
  siempre).
- `HCallbackProxy.getLaunchActivityItem` delega en `LaunchItemLocator`. El log de fallo (R05.6) se emite una sola
  vez por proceso (flag estático) e incluye `Build.VERSION.SDK_INT` y la lista de campos de `ClientTransaction`.
- `B/utils/SystemHookManager.hookClientTransactionListenerController` (hook de
  `ClientTransactionListenerController`) no se toca; solo se anota que depende de otra clase de Android 15+.

**Alternativas.** Interceptar a nivel `ActivityThread.handleLaunchActivity` en vez de `mH`: más estable pero
reescribe el flujo de `HCallbackProxy`; descartada para esta spec. Esperar a ver Android 17 real para elegir:
rechazado porque la cadena de respaldo es barata y no cambia con la versión.

**Riesgos.** La estructura de `LaunchActivityItem` (`mIntent`, `mInfo`) puede cambiar en 17 (sin verificar):
si falta un campo, `R05.6` lo deja en el log. Pruebas necesarias en un dispositivo con Android 17 (aún por usar).

## 3. JniHook autocomprobación (R05.8 a R05.10)

**Hoy.** `C/JniHook/JniHook.cpp` `JniHook::InitJniHook(env, api_level)` es `void`. Escanea las palabras de dos
`ArtMethod` señuelo (`native_offset`, `native_offset2`) para obtener `art_method_size`, el offset del puntero nativo
y el de los `access_flags` (busca el valor `kAccPublic|kAccStatic|kAccNative|kAccFinal` más `kAccPublicApi` en
API ≥ 29 y `kAccNterpInvokeFastPathFlag` en API ≥ 31), y lo mismo para `ArtField`. Si algo no aparece hace
`ALOGE("init jni hook error...")` y **sale sin avisar**. Lagunas verificadas en el código:
- `HookJniFun` solo comprueba `art_method_native_offset == 0`; si falla la búsqueda de `art_method_flags_offset`
  (queda 0) los hooks siguen usando el offset 0 y pueden leer o escribir mal los flags.
- Si `art_method_size` sale 0 o enorme (señuelos no contiguos), el bucle ya es inseguro.
- `NativeCore.init(int)` (`C/BoxCore.cpp`, `init`) es `void`; Java no sabe el resultado.

**Cambio.**
- `InitJniHook` devuelve `bool` y fija `HookEnv.healthy`. Validaciones: `art_method_size` entre 16 y 256 bytes y
  múltiplo de 4/8; `art_field_size` entre 8 y 64; los tres offsets > 0 y < tamaño; el puntero en
  `artMethod[native_offset]` coincide con `native_offset`.
- `HookJniFun` retorna sin hacer nada si `!HookEnv.healthy` (hoy solo mira un offset).
- Nueva nativa `NativeCore.isJniHookHealthy(): boolean`; `NativeCore.init` pasa a devolver `boolean`
  (descriptor `(I)Z`, cambiar `gMethods` en `C/BoxCore.cpp`).
- Prueba funcional (R05.10), en `B/core/IOCore.enableRedirect` tras `NativeCore.enableIO()`: resolver con
  `new File("/data/data/<pkg>/.bb_probe").getCanonicalPath()` y comprobar que empieza por el directorio virtual
  del usuario (`BEnvironment.getDataDir`). Resultado a `Slog`.
- Aviso visible: el estado sano/no sano se calcula en el proceso principal del host (donde también se llama
  `NativeCore.init`, `BlackBoxCore.java` ~l.1728) y se expone con `BlackBoxCore.isIoRedirectHealthy()`
  (nuevo). `app/src/main/java/top/niunaijun/blackboxa/view/main/MainActivity.kt` muestra un banner permanente
  (no un `Toast`) con texto del tipo "La redirección de ficheros no está activa en esta versión de Android; las
  apps del box pueden escribir fuera de su espacio". Los offsets son los mismos en todos los procesos del
  mismo dispositivo, por lo que el resultado del host es representativo (sin verificar en procesos que
  arrancan antes de un reinicio de ART).
- Con `healthy == false` el motor no instala los hooks de `UnixFileSystem`, pero `OsStub` sigue redirigiendo
  rutas Java; la decisión de bloquear el lanzamiento de apps (o solo avisar) queda en preguntas abiertas.

**Alternativas.** Escáner con firmas por versión de ART: no mantenible. Sustituir por un hook por inline
(Dobby) en `UnixFileSystem`: cambio de arquitectura, fuera de alcance. Abortar el arranque del box: demasiado
agresivo hoy.

**Riesgos.** Falsos negativos del escáner en ART nuevos (mainline): son justo el caso que se quiere cazar.
Falsos positivos (límites mal elegidos) harían desaparecer una redirección que funciona: los límites se
validan con el Android 16 de referencia en T05.5 antes de fijarlos.

## 4. Diálogo de VPN (R05.11, R05.12)

**Hoy.** `app/src/main/java/top/niunaijun/blackboxa/view/main/MainActivity.kt` llama a `checkVpnPermission()`
desde `onCreate` sin condición: `VpnService.prepare(this)` y, si devuelve intent, lanza el diálogo
(`vpnPermissionResult`). Mientras tanto el motor solo arranca el servicio VPN si
`ClientConfiguration.isUseVpnNetwork()` es `true` (`BlackBoxCore.initVpnService`); `BlackBoxLoader.mUseVpnNetwork`
vale `false` por defecto. El diálogo, por tanto, se muestra aunque la función esté apagada. `ProxyVpnService` y su
permiso `BIND_VPN_SERVICE` en `Bcore/src/main/AndroidManifest.xml` no se tocan.

**Cambio.** Condicionar la llamada: `if (BlackBoxLoader().useVpnNetwork()) checkVpnPermission()` (o equivalente
sin crear un loader nuevo; ajustar a la API real, sin verificar). Si existe un interruptor de ajustes para
`invalidUseVpnNetwork(enable)`, pedir el permiso en ese momento (R05.12). Sin cambios de motor.

**Alternativas.** Quitar el código de VPN por completo: se descarta aquí porque `ProxyVpnService` y los proxies
(`IVpnManagerProxy`, `VpnCommonProxy`) pueden usarse por apps invitadas; ver R05.18.

## 5. VirtualSpoof: propuesta opt-in por app (R05.13 a R05.17)

**Hoy.** `C/Utils/VirtualSpoof.cpp` instala en un `__attribute__((constructor))`, al cargar `libblackbox.so`, un hook
Dobby sobre `__system_property_get` de libc. Cualquier proceso que cargue la biblioteca, incluido el host y `:black`,
recibe un perfil fijo: `ro.product.model=Pixel 6`, `ro.product.device=oriole`,
`ro.build.fingerprint=google/oriole/oriole:12/SP1A.210812.015/7679548:user/release-keys`,
`ro.build.version.release=12`, `ro.build.version.security_patch=2022-01-05`, `ro.serialno` fijo y otras 14 claves.
Las claves de SDK (`ro.build.version.sdk`) no se suplantan: la mezcla (release 12, SDK real 36) es incoherente.
Está en `C/Android.mk` (línea de `Utils/VirtualSpoof.cpp`).

**Qué se desconoce (sin verificar) y se mide primero (T05.10).** En Android 12+ `SystemProperties` de Java lee con
`__system_property_find` + `__system_property_read_callback`, no con `__system_property_get`; y `android.os.Build`
se inicializa en zigote antes de que el hook exista. Si es así, el spoof actual afecta solo a código nativo de la
app invitada que llame a `__system_property_get`, y no a `Build.MODEL` ni a `SystemProperties.get`. La tabla a
rellenar con la app de prueba, con spoof activado y desactivado:

| Lectura desde la app invitada | Esperado hoy | Medido |
|---|---|---|
| `Build.MODEL` / `Build.FINGERPRINT` | real (Build ya cargado) | pendiente |
| `SystemProperties.get("ro.product.model")` por reflexión | por medir | pendiente |
| `getprop ro.product.model` ejecutando `/system/bin/getprop` | real (otro proceso) | pendiente |
| JNI `__system_property_get` en una `.so` de prueba | suplantado | pendiente |

**Decisión propuesta.** Pasar a **opt-in por app y por usuario virtual**, desactivado por defecto, incluidas las
apps de Google (R05.15). Motivos: el perfil es incoherente con el SDK real, no mejora el nivel de Play Integrity
con GMS dentro (riesgo R13, spec 08 lo medirá) y puede romper apps que comprueban `ro.build.version.release` frente
a `Build.VERSION.SDK_INT`; además se aplica hoy al host y al servidor sin motivo.

**Cambio.**
- `C/Utils/VirtualSpoof.cpp`: quitar el constructor; nuevas funciones `bool virtual_spoof_enable()` (instala el hook
  con Dobby una sola vez, protegido por `std::once_flag`) y un `std::atomic<bool> g_spoof_active`. El hook consulta
  `g_spoof_active` antes de sustituir. Nueva nativa `NativeCore.setPropertySpoof(boolean)`
  (registrar en `gMethods` de `C/BoxCore.cpp`).
- `B/app/BActivityThread.handleBindApplication`: tras `NativeCore.init(...)`, si el servidor indica que la opción
  está activa para `(packageName, userId)`, llamar a `NativeCore.setPropertySpoof(true)`. El host y `:black` nunca
  lo llaman (R05.13).
- Almacén de la opción: fichero nuevo `BEnvironment.getAppFlagsConf()` gestionado en el servidor `:black` por una
  clase nueva `B/core/system/pm/AppFlagsStore` (mapa `usuario:paquete -> bits`, con `AtomicFile`, no se toca el parcel
  de `BPackageSettings` por la razón explicada en la spec 01 D2). Dos métodos nuevos en `IBPackageManagerService.aidl`:
  `boolean getAppFlag(String pkg, int userId, int flag)` y `void setAppFlag(String pkg, int userId, int flag, boolean on)`.
  Las apps ya en ejecución aplican el cambio al reiniciar.
- UI (`app`): interruptor "Simular dispositivo Pixel 6" en las opciones de cada app (texto exacto a decidir).

**Alternativas.** (a) Mantener el comportamiento global: sin beneficio demostrado y rompe R05.13. (b) Eliminarlo del
todo: se pierde una herramienta que la spec 08 puede querer probar; el coste de mantenerlo opt-in es bajo.
(c) Perfil coherente (Pixel con Android 16 y parche reciente): requiere decidir qué dispositivo y mantenerlo al día;
se aplaza a la spec 08.

**Riesgos.** Una `.so` nativa que cachee propiedades antes de la activación (el hook se instala en `bindApplication`,
antes de cargar código de la app; bajo riesgo). Dobby sobre una función de libc sin probar en páginas de 16 KB
(riesgo R12): se prueba en T05.10 con la opción activada.

## 6. Código muerto (R05.18)

Candidatos verificados por lectura (no se eliminan por defecto):
- `C/Hook/FileSystemHook.cpp` y `C/Utils/AntiDetection.cpp`: resuelven símbolos con `xdl` pero no llaman a
  `DobbyHook` ([arquitectura-motor.md](../../fase0/arquitectura-motor.md) §3). `AntiDetection.cpp` además ejecuta un
  constructor en todos los procesos. Se eliminan **solo si** estorban a la alineación de 16 KB o a una compilación.
- `C/Hook/RuntimeHook.cpp`: existe pero no se compila (no figura en `C/Android.mk`).
- `B/fake/service/GmsProxy` y `GoogleAccountManagerProxy`: registrados en `HookManager` (`addInjector`); su utilidad
  la decide la spec 03.
Sin acción prevista en esta spec salvo que T05.14 demuestre lo contrario.

## 7. Toolchain (R05.19 a R05.21)

Estado actual: `compileSdkVersion = 35`, `targetSdkVersion = 28`, `minSdk = 21` (raíz `build.gradle`); AGP `8.13.2`,
Kotlin `1.9.23` (`gradle/libs.versions.toml`); JDK 21, NDK `29.0.13846066`.
Plan: intentar `compileSdk = 36` en una rama; si `:Bcore` y `:app` compilan, dejarlo. Android 17 posiblemente sea
API 37 (sin verificar): no hace falta compilar contra él, porque la reflexión no necesita símbolos del SDK. Kotlin solo
sube si el compilador lo exige (R05.20). Riesgo: AGP puede avisar de `compileSdk` por encima de lo probado; se acepta
mientras el APK mantenga `targetSdk` 28. Cambio de `targetSdk` explícitamente fuera de alcance (R8).

## 8. Plan de pruebas

- **Unitarias JVM:** `LaunchItemLocatorTest` (tres variantes de `ClientTransaction` simuladas),
  `HiddenApiGateTest` (fallo simulado).
- **Instrumentadas:** `JniHookSelfCheckTest` (sano en Android 16; fallo forzado por propiedad de depuración),
  prueba de ruta (R05.10).
- **App de prueba:** ampliar NotifProbe para volcar `Build.*`, `SystemProperties` y una lectura JNI de propiedades (T05.10).
- **Dispositivo (propietario):** Android 16 (referencia, OPPO) y Android 17 (por llegar): `scripts/fase0/diag.sh`
  tras cada grupo de cambios; criterios de R05.22 y del hito M1 del [ROADMAP](../ROADMAP.md).
- **CI:** compilación, comprobación de alineación a 16 KB, firma y ausencia de telemetría (ADR-0006).
