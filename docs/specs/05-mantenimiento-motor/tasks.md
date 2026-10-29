# Spec 05: mantenimiento del motor — tareas

Estado: Borrador

Orden de ejecución; cada tarea ≤ 1 día y termina en un commit pequeño con la CI en verde.
Rutas relativas a `engine/`. Las tareas T05.1 a T05.9 son la parte bloqueante del hito M1 y preceden a la spec 01.

| ID | Tarea | Cubre | Verificación | Días |
|---|---|---|---|---|
| T05.0 | **Hecha.** Eliminar la telemetría del upstream (commit `8aa976d`). Solo falta confirmar en la CI que la comprobación de ADR-0006 la vigila | R05.1 | Búsqueda de `sendLogs`/`LogSender` sin resultados; comprobación de CI | 0 |
| T05.1 | Condicionar `checkVpnPermission()` en `MainActivity.kt` a la opción de VPN y pedirla al activarla | R05.11, R05.12 | Instalación limpia en Android 16, 5 aperturas sin diálogo | 0.5 |
| T05.2 | `HiddenApiGate` + dependencia `org.lsposed.hiddenapibypass`; sustituir la llamada en `setEssentialProperties` (dejar FreeReflection y el nativo de momento) | R05.2, R05.3 | `HiddenApiGateTest`; logcat sin `Didn't find setHiddenApiExemptions` en Android 16 | 1 |
| T05.3 | Retirar FreeReflection y `NativeCore.disableHiddenApi` (nativo y Java) tras comprobar Android 15 y 16 (y 17 cuando haya dispositivo) | R05.4, R05.21 | `dependencies` sin FreeReflection; CI de alineación 16 KB | 0.5 |
| T05.4 | `LaunchItemLocator` y campos nuevos en `ClientTransaction.java`; `HCallbackProxy` delega | R05.5, R05.6, R05.7 | `LaunchItemLocatorTest`; abrir NotifProbe en Android 16 | 1 |
| T05.5 | `InitJniHook` devuelve `bool` con límites y `HookEnv.healthy`; `HookJniFun` respeta el estado; `NativeCore.init` devuelve `boolean` y nueva `isJniHookHealthy` | R05.8 | `JniHookSelfCheckTest` sano en Android 16 | 1 |
| T05.6 | Prueba funcional de ruta en `IOCore.enableRedirect` y `BlackBoxCore.isIoRedirectHealthy()` | R05.9, R05.10 | Prueba instrumentada con fallo forzado: `isIoRedirectHealthy()` es `false` | 0.5 |
| T05.7 | Banner permanente en `MainActivity.kt` + textos en `strings.xml` | R05.9 | Fallo forzado: banner visible; sin fallo: ausente | 0.5 |
| T05.8 | Probar `compileSdk = 36` en la raíz y corregir lo que rompa; decidir Kotlin | R05.19, R05.20 | CI verde; `targetSdkVersion:'28'` en el APK | 0.5 |
| T05.9 | Validación del grupo en dispositivo (propietario): `diag.sh` en Android 16, y en Android 17 si ya hay móvil | R05.22, R05.5 a R05.10 | Evidencias adjuntas; hito M1 (parte motor) | 0.5 |
| T05.10 | Medir VirtualSpoof: ampliar NotifProbe para volcar `Build.*`, `SystemProperties` y lectura JNI; rellenar la tabla de design.md §5; probar Dobby con página de 16 KB si hay dispositivo | R05.17 | Tabla rellenada en design.md | 0.5 |
| T05.11 | Spoof opt-in nativo: quitar el constructor de `VirtualSpoof.cpp`, `virtual_spoof_enable`, `NativeCore.setPropertySpoof` | R05.13, R05.15 | App de prueba sin opción: valores reales en host y en invitada | 1 |
| T05.12 | Almacén de la opción: `AppFlagsStore`, `BEnvironment.getAppFlagsConf()`, métodos AIDL `getAppFlag`/`setAppFlag`, llamada desde `BActivityThread.handleBindApplication` | R05.14, R05.16 | Test instrumentado de persistencia; activar y comprobar el perfil en la app de prueba | 1 |
| T05.13 | Interruptor por app en la UI (`app`) y textos | R05.16 | Paso manual: activar, reiniciar el box, sigue activo | 0.5 |
| T05.14 | Revisar código muerto: confirmar si algún candidato de design.md §6 estorba; eliminar solo con evidencia | R05.18 | Revisión del propietario; si no estorba, nota en el commit y sin cambios | 0.5 |
| T05.15 | Cierre: estados a "Implementado", actualizar R5, R6, R7, R13 en `docs/fase0/riesgos-android.md` si procede | todos | Revisión del propietario | 0.5 |

**Total estimado: 10 días** (sin T05.0, ya hecha). Parte bloqueante para M1 (T05.1 a T05.9): 6 días.
Los grupos T05.10 a T05.14 (VirtualSpoof y limpieza) no bloquean M1 y pueden pasar a después de la spec 01 si el
propietario lo prefiere.

## Notas

- T05.2 y T05.4 son independientes; T05.5 a T05.7 forman otra cadena. Se pueden intercalar.
- T05.3 solo se hace cuando T05.2 se haya verificado en un dispositivo; hasta entonces FreeReflection queda como red.
- No se toca `targetSdk` (28) en ninguna tarea.
