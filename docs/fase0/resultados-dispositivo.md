# Fase 0: resultados en un dispositivo real (29-10-2026)

## Dispositivo

| Dato | Valor |
|---|---|
| Modelo | OPPO CPH2841 (ROM EEA, ColorOS) |
| Build | CPH2841_16.0.10.602(EX01) |
| Android | 16 (API 36) |
| ABI | solo arm64-v8a, sin 32 bits |
| Tamaño de página | 4 KB |
| GMS en el sistema real | sí: GMS, GSF, Play Store, Gmail y WhatsApp instalados |

Motor: `BlackBox_4.0.0_arm64-v8a-debug.apk` compilado del upstream `89b5983` sin
cambios.

Todo se ejecutó por adb inalámbrico, sin iniciar sesión en ninguna cuenta y sin
aceptar condiciones de ninguna app.

## Resultados

| Prueba | Resultado | Evidencia |
|---|---|---|
| Instalar el motor | OK | `adb install` correcto; `targetSdk=28` |
| Arranque del motor | OK con avisos | `SystemCallProvider: BlackBox system initialized successfully` |
| Bypass nativo de API ocultas | **FALLA** | `E NativeCore: HiddenAPI: Didn't find setHiddenApiExemptions in any form` |
| Acceso a API ocultas (greylist) | OK | `hiddenapi: Accessing hidden method ... api=unsupported ... allowed`; ningún `denied` |
| Diálogos al abrir | Molestia | Pide crear una VPN **en cada arranque** y "All files access". Se denegó la VPN y se pospuso el permiso de archivos |
| Instalar APK propio (NotifProbe) desde archivo | OK | Aparece en "User 0" |
| Lanzar app invitada | OK | `ProxyActivity$P0`, proceso `:p0` |
| Logs del invitado | No visibles | Ni `Log.i` ni `System.err` del invitado aparecen en logcat |
| Notificación del invitado llega al sistema | OK | `NotificationRecord(pkg=top.niunaijun.blackbox ... channel=probe_msgs@black-0 ... actions=2)` |
| Nombre de app invitada en la notificación | **NO** | La cabecera muestra "BlackBox" |
| Dos `notify(tag, 7)` con tags distintos | **Se pisan** | Una sola entrada, `tag=null id=66774752` |
| Icono pequeño | **Incorrecto** | `Icon(typ=RESOURCE pkg=top.niunaijun.blackbox id=0x7f0e0000)`, que en el host es `mipmap/ic_launcher` |
| Tocar la notificación | OK | `ProxyPendingActivity$P0` → la invitada recibe su intent (`CONTENT_INTENT_OK`) |
| Responder (RemoteInput por broadcast) | **FALLA** | No llega nada y no hay ninguna traza |
| Responder (RemoteInput por servicio) | **FALLA** | `W ActivityManager: Unable to start service Intent { ... cmp=dev.probe.notif/.ReplyService clip={...} } mCallingUid=10057` |
| Clonar GMS del sistema (GMS Manager) | Se instala | Play Store, Servicios de Google Play, GSF, ConfigUpdater, Partner Setup... |
| Abrir Play Store dentro | **CRASH** | `ClassNotFoundException: com.android.vending.AssetBrowserActivity ... DexPathList[[zip file ".../base.apk"` (faltan splits) |
| GMS dentro | **CRASH en bucle** | `FATAL:external/cronet/stable/base/android/jni_android.cc:220 Uncaught Java exception in native code` (SIGTRAP), y `Permission Denial: opening provider com.google.android.gms.common.appdoctor.AppDoctorProvider ... requires ...INTERNAL_BROADCAST` |
| WhatsApp clonado del sistema (5 splits) | Arranca | Pantalla "Te damos la bienvenida a WhatsApp" estable. No se registró |

## Lectura

- **El núcleo del motor funciona en Android 16:** proxies de binder, procesos stub,
  lanzamiento de actividades, IO y publicación de notificaciones.
- **No funcionan:**
  - los splits;
  - GMS dentro del box;
  - las acciones de notificación que no son actividades;
  - la identificación de la app invitada en la notificación.
- Esto coincide con el análisis estático. Ninguno de estos fallos se debe a un cambio
  de Android 16; son carencias del motor en cualquier versión.

## Nota sobre telemetría

Durante la sesión hubo crashes de apps invitadas, y el upstream envía `logcat` a un
servidor externo cuando detecta un crash
([arquitectura-motor.md](arquitectura-motor.md) §8).
- No se encontraron trazas de envío (`Sending logs to`, `Logs sent successfully`) en
  los buffers de logcat.
- Tampoco había ficheros `crash_log_*` en la caché del motor.
- No se puede descartar del todo. Los logs del host durante la prueba solo contenían
  datos de las apps de prueba y del GMS clonado, sin sesión iniciada.

## Limpieza del dispositivo

```bash
adb uninstall top.niunaijun.blackbox   # borra también todo el contenido del box
adb shell rm /sdcard/Download/probe.apk
```
