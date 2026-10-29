# Spec 05: mantenimiento del motor — requisitos

Estado: Borrador

Contexto: [riesgos-android.md](../../fase0/riesgos-android.md) R5, R6, R7, R8, R13, R14;
[arquitectura-motor.md](../../fase0/arquitectura-motor.md) §2, §4, §5;
[resultados-dispositivo.md](../../fase0/resultados-dispositivo.md). Esta spec es la parte bloqueante del hito M1
y precede a la spec 01.

Sistemas: *el motor*, *el host*, *el box*, *la app invitada* (ver [README](../README.md)).

## Alcance

Dentro: bypass de APIs ocultas, `ClientTransaction` en Android 17, autocomprobación de JniHook, diálogo de VPN,
suplantación de propiedades (VirtualSpoof), código muerto solo si estorba, toolchain (compileSdk, Kotlin).

Fuera: `targetSdk` del host (se mantiene 28, riesgo R8 sin cambios), cambios de GMS/GSF (spec 03),
notificaciones (spec 02), `GmsProxy` y `GoogleAccountManagerProxy` (código aparentemente muerto, pero lo decide la spec 03).

## Requisitos

### Telemetría (ya hecha)

**R05.1** THE motor SHALL NOT enviar logs ni datos a servidores externos.
- **Estado: implementado** en el commit `8aa976d` del motor ([ADR-0003](../../adr/0003-eliminar-telemetria-upstream.md)).
  En el árbol actual no quedan `sendLogs` ni `LogSender` (comprobado con búsqueda de texto).
- Verificación: la comprobación de CI de ADR-0006 que falla si reaparece la telemetría.

### APIs ocultas

**R05.2** WHEN el motor arranca en cualquier proceso (host, `:black`, `:pN`) THE motor SHALL desbloquear las
APIs ocultas con `org.lsposed.hiddenapibypass.HiddenApiBypass` (exención `"L"`) en lugar de
`NativeCore.disableHiddenApi()` y de FreeReflection.
- Verificación: en el Android 16 de referencia no aparece `HiddenAPI: Didn't find setHiddenApiExemptions`
  en logcat, y los proxies de `HookManager` se inyectan (paso (a) del [plan de pruebas](../../fase0/plan-pruebas.md)).

**R05.3** IF la llamada de desbloqueo falla THEN THE motor SHALL registrar un aviso con el modelo y la versión
de Android y continuar el arranque (el `targetSdk` 28 del host permite la greylist).
- Verificación: test instrumentado que simula el fallo (`HiddenApiGateTest`); el box arranca igual.

**R05.4** THE motor SHALL quitar la dependencia `com.github.tiann:FreeReflection` y la función nativa
`disableHiddenApi` cuando R05.2 esté verificada en Android 15, 16 y 17.
- Verificación: `./gradlew :Bcore:dependencies` (en CI) no lista FreeReflection; el `.so` conserva el
  alineamiento de 16 KB.

### ClientTransaction

**R05.5** WHEN llega un `EXECUTE_TRANSACTION` THE motor SHALL localizar el `LaunchActivityItem` probando, en
orden, `ClientTransaction.getTransactionItems()`, el campo `mTransactionItems` y el campo `mActivityCallbacks`.
- Verificación: test unitario con dobles de las tres variantes (`LaunchItemLocatorTest`); en dispositivo,
  abrir una app invitada en Android 16 y en Android 17.

**R05.6** IF ninguna variante devuelve el item THEN THE motor SHALL registrar un único error con la versión de
Android y los nombres de campos de `ClientTransaction` encontrados por reflexión, y SHALL NOT
fallar con excepción no controlada en el hilo principal.
- Verificación: test con un `ClientTransaction` sin ninguna de las variantes; el log tiene una sola línea de error.

**R05.7** WHEN el item se localiza por `getTransactionItems`/`mTransactionItems` THE motor SHALL leer `mIntent`
y `mInfo` del `LaunchActivityItem` y reescribirlos igual que hoy, y el token por el mismo mecanismo
(`mActivityToken`).
- Verificación: paso de lanzar actividad de NotifProbe en Android 16 y 17 con `ProxyActivity$P0`.

### JniHook

**R05.8** WHEN el motor inicializa los hooks nativos THE motor SHALL comprobar que `art_method_native_offset`,
`art_method_flags_offset` y `art_field_flags_offset` se han encontrado y están dentro de los límites del tamaño
de `ArtMethod`/`ArtField` calculado, y exponer el resultado a Java.
- Verificación: test instrumentado `JniHookSelfCheckTest` en el Android 16 de referencia (debe dar sano).

**R05.9** IF la autocomprobación falla THEN THE motor SHALL desactivar la instalación de hooks JNI y SHALL
mostrar un aviso visible en el host (banner en la pantalla principal) indicando que la redirección de
ficheros no está activa; SHALL NOT arrancar apps invitadas con redirección a medias sin avisar.
- Verificación: instrumentación que fuerza el fallo (propiedad de depuración); el banner aparece y se registra
  `init jni hook error`.

**R05.10** WHEN el hook de `UnixFileSystem` se instala THE motor SHALL verificar su efecto con una ruta de
prueba (`/data/data/<paquete>/...` resuelta al directorio virtual) y tratar un resultado erróneo como R05.9.
- Verificación: la misma prueba instrumentada, con y sin hook.

### VPN

**R05.11** THE host SHALL NOT mostrar el diálogo de permiso de VPN al abrir la pantalla principal a menos que
el usuario haya activado la opción "usar red VPN" (`BlackBoxLoader.useVpnNetwork()`, hoy `false` por defecto).
- Verificación: instalación limpia en el Android 16 de referencia; abrir 5 veces la app sin diálogo de VPN.

**R05.12** WHEN el usuario activa la opción de VPN THE host SHALL pedir el permiso en ese momento, no en cada
arranque.
- Verificación: activar la opción, conceder, reiniciar la app; no vuelve a pedir.

### VirtualSpoof

**R05.13** THE motor SHALL NOT suplantar propiedades del sistema (`__system_property_get`) en el host ni en
el proceso `:black`.
- Verificación: `getprop`/`SystemProperties` leídos desde el host devuelven los valores reales (prueba con
  la app de prueba en el proceso principal).

**R05.14** WHERE el usuario ha activado la suplantación para una app (por usuario virtual) THE motor SHALL
aplicar el perfil de propiedades actual (Pixel 6, Android 12) solo en los procesos de esa app.
- Verificación: app de prueba con la opción activada vuelca propiedades y `Build.*`; con la opción
  desactivada, los valores reales.

**R05.15** THE motor SHALL tener la suplantación **desactivada por defecto** para toda app, incluidas las de Google.
- Verificación: instalación limpia; ninguna app con la opción activada.

**R05.16** THE host SHALL ofrecer un interruptor por app para esa opción y SHALL persistir el valor entre
reinicios del motor.
- Verificación: activar, reiniciar el box, comprobar que sigue activo (paso manual).

**R05.17** WHEN se haya medido qué lecturas alcanza el hook (propiedades vía libc, `SystemProperties` de Java,
`Build.*`) THE motor SHALL documentar el resultado en design.md.
- Verificación: tarea T05.10 con la app de prueba, tabla en design.md §5.

### Código muerto

**R05.18** THE motor SHALL NOT eliminar código solo por parecer muerto; SHALL eliminar un fragmento únicamente
si bloquea una compilación, una prueba de esta spec o la alineación a 16 KB, y registrar la razón en el commit.
- Verificación: revisión del propietario del diff de la tarea T05.14.

### Toolchain

**R05.19** WHEN una dependencia, una API del SDK usada por el motor o la CI exijan `compileSdk` 36 THE motor
SHALL subir `compileSdk` a 36 sin cambiar `targetSdk` (28) ni `minSdk`.
- Verificación: la CI compila `:Bcore` y `:app`; `aapt dump badging` del APK sigue indicando `targetSdkVersion:'28'`.

**R05.20** IF Kotlin 1.9.23 no compila con `compileSdk` 36 o con el AGP en uso THEN THE motor SHALL actualizar
Kotlin a la versión mínima compatible; si no, SHALL mantenerlo.
- Verificación: salida de compilación de T05.11 adjunta al commit.

**R05.21** THE motor SHALL conservar las `.so` alineadas a 16 KB tras todos los cambios nativos de esta spec.
- Verificación: la comprobación de CI de ADR-0006.

### Regresión

**R05.22** WHEN termina cada grupo de cambios THE motor SHALL pasar los pasos del plan de pruebas de la fase 0
que ya pasaban (instalar APK propio, lanzar actividad, notificación llega y abre la app) en Android 16.
- Verificación: ejecución de `scripts/fase0/diag.sh` por el propietario; hito M1.
