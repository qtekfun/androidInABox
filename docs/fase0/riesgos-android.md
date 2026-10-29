# Fase 0: riesgos en Android 15, 16 y 17

Leyenda:
- **Probabilidad**: alta, media o baja de que rompa.
- **Estado**:
  - *medido*: comprobado en el OPPO CPH2841 con Android 16, ver
    [resultados-dispositivo.md](resultados-dispositivo.md);
  - *código*: deducido del código del motor o de AOSP;
  - *hipótesis*: sin verificar.

## Resumen

| # | Riesgo | Versiones | Prob. | Estado | Impacto en la cadena crítica |
|---|---|---|---|---|---|
| R1 | Sin soporte de split APK | todas | — | **medido: rompe** | Play Store, GMS, Gmail y WhatsApp clonados del sistema |
| R2 | GMS dentro del box no funciona | todas | alta | **medido: rompe** | Pasos b, c, d y h |
| R3 | Respuesta desde notificación (PendingIntent broadcast/servicio) | todas | — | **medido: rompe** | Pasos f y g |
| R4 | Notificación sin nombre de app ni perfil, tag ignorado, icono incorrecto | todas | — | **medido** | Paso e |
| R5 | Bypass nativo de API ocultas | 16 (y 15) | — | **medido: falla, con fallback** | Robustez general |
| R6 | `ClientTransaction.mActivityCallbacks` | 17+ | media | código (AOSP) | Arranque de actividades |
| R7 | Escaneo heurístico de `ArtMethod` (JniHook) | 16, 17 | media | código | Redirección de IO |
| R8 | targetSdk 28 del host | 17+ | baja hoy, alta a futuro | código | Instalación del motor |
| R9 | Procesos invitados sin servicio en primer plano real | todas, peor en ROM china | alta | código | Recibir mensajes (d) |
| R10 | Límites de memoria por app | 17 | media | hipótesis | Varias apps a la vez |
| R11 | Protección contra redirección de intents | 16+ | baja | hipótesis | `ProxyActivity` relanza un intent de los extras |
| R12 | Páginas de 16 KB | 15+ | baja | medido parcialmente | `.so` propia OK; Dobby sin probar |
| R13 | Suplantación de propiedades (Pixel 6 / Android 12) | todas | alta para Play Integrity | código | Paso h |
| R14 | Telemetría del upstream | todas | — | código | Privacidad |

## Detalle

### R1. Split APK (rompe en todas las versiones)
- `BPackageManagerService` instala solo un APK. `CopyExecutor` copia solo el base y
  extrae las libs nativas solo de él. `PackageManagerCompat.generateApplicationInfo`
  nunca rellena `splitSourceDirs` ni `splitNames`.
- En Android moderno casi todas las apps de Play vienen en splits. En el OPPO:
  - Play Store: 8 APK
  - GMS: 13 APK (base + splits `*_installtime`: Dynamite, Cronet, GoogleCertificates...)
  - Gmail y WhatsApp: 5 APK cada una
- Medido: Play Store clonado falla con `ClassNotFoundException:
  com.android.vending.AssetBrowserActivity ... DexPathList[[zip file ".../base.apk"`.
- **Arreglo:**
  - Leer `splitSourceDirs` del `ApplicationInfo` del host al clonar.
  - Aceptar `.apks`/`.apkm`/`.xapk` al instalar desde archivo.
  - Pasar los splits a `LoadedApk`: `ApplicationInfo.splitSourceDirs` y
    `splitNames`, con un classloader que los incluya.
- Esfuerzo: 1–2 semanas.

### R2. GMS dentro del box (rompe)
- El motor solo clona el GMS del host. En una ROM china sin GMS no hay nada que clonar.
  Instalar GMS desde un APK suelto choca con R1, porque GMS es un bundle de splits.
- Los providers de GMS se dejan pasar al GMS **real**. Medido: `Permission Denial:
  opening provider com.google.android.gms.common.appdoctor.AppDoctorProvider ...
  requires com.google.android.gms.permission.INTERNAL_BROADCAST`.
- Medido: el proceso GMS del box muere repetidamente con
  `FATAL:external/cronet/stable/base/android/jni_android.cc:220 Uncaught Java exception
  in native code` (SIGTRAP). Play Store muere con `ClassCastException`.
- GMS espera permisos `signature|privileged`, un proceso persistente y la identidad de
  sistema de GSF. Ningún motor abierto conocido lo ejecuta de forma estable dentro de
  un sandbox.
- **Alternativa:** microG.
  - Necesita que el `PackageManager` virtual declare la firma de Google para
    `com.google.android.gms` (signature spoofing). Es fácil aquí, porque el motor
    controla `getPackageInfo`.
  - Necesita desactivar el passthrough de providers cuando GMS es virtual.
  - Necesita mantener vivo su proceso para FCM.
- Esfuerzo:
  - microG funcional: 2–4 semanas.
  - GMS real: indeterminado, mínimo 1–2 meses, con riesgo alto de no lograrlo.

### R3. Respuesta rápida (rompe)
- `GetIntentSender` solo reescribe PendingIntents de actividad.
- Medido:
  - Acción por servicio: `W ActivityManager: Unable to start service Intent {
    cmp=dev.probe.notif/.ReplyService clip=... }`.
  - Acción por broadcast: se pierde sin traza.
- Si la app real está instalada fuera del box, la acción **iría a la app real**.
- **Arreglo:**
  1. Reescribir `INTENT_SENDER_BROADCAST`, `SERVICE` y `FOREGROUND_SERVICE` hacia un
     receiver stub del host, guardando el intent original como hace
     `ProxyPendingRecord`.
  2. En el stub, copiar el *fill-in* que añade el sistema (extras y `ClipData` de
     RemoteInput) al intent original.
  3. Despacharlo dentro del box.
  4. Los PendingIntents deben ser `FLAG_MUTABLE` en el host para que el fill-in llegue.
- Esfuerzo: 1 semana.

### R4. Presentación de la notificación (paso e)
- Medido:
  - Cabecera "BlackBox".
  - Dos tags → una notificación.
  - Icono `Icon(RESOURCE pkg=top.niunaijun.blackbox id=0x7f0e0000)`.
- Android no permite cambiar el nombre de app de la cabecera.
  `EXTRA_SUBSTITUTE_APP_NAME` exige `SUBSTITUTE_NOTIFICATION_APP_NAME`, un permiso
  privilegiado.
- **Arreglo:**
  - Convertir `smallIcon` y `largeIcon` a bitmap en el proceso invitado.
  - Poner el icono de la app invitada como `largeIcon` y
    `subText = "<App> · <Perfil>"`.
  - Canales `<pkg>:<canal>@<user>` con nombre `"<App> (<Perfil>) · <canal>"`.
  - Incluir el tag en la clave.
  - Persistir la tabla de canales.
- Esfuerzo: 1 semana.
- Para el reloj (Huawei Health): filtra y muestra por **paquete emisor**. Todas las
  notificaciones llegarán como "BlackBox" y se activan o desactivan en bloque.
  **Hipótesis:** el subText y el título sí se ven en el reloj.

### R5. API ocultas (falla con fallback)
- Medido en Android 16: `HiddenAPI: Didn't find setHiddenApiExemptions in any form`.
- Las llamadas a la greylist siguen permitidas (`api=unsupported ... allowed`) gracias
  al targetSdk 28 del host, y FreeReflection queda como fallback.
- En la sesión no se vio ningún `hiddenapi ... denied`.
- **Arreglo:** sustituir ambos mecanismos por `org.lsposed.hiddenapibypass`, que se
  mantiene activamente. El `build.gradle` raíz ya declara `hiddenApiBypass = '4.3'`
  sin usarlo.
- Esfuerzo: 1–2 días.

### R6. `ClientTransaction` (Android 17+)
- `HCallbackProxy.getLaunchActivityItem` lee `mActivityCallbacks`.
- En `android16-release` y `main` (consultado el 29-10-2026) el campo existe, pero:
  - marcado `@Deprecated @UnsupportedAppUsage(maxTargetSdk = UPSIDE_DOWN_CAKE)`;
  - con el TODO `b/324203798` para eliminarlo;
  - se rellena "para compatibilidad" desde `addTransactionItem`.
- **Arreglo:** leer `getTransactionItems()` / `mTransactionItems`, con fallback.
- Esfuerzo: 2–3 días.

### R7. JniHook
- Los offsets de `ArtMethod` se calculan escaneando memoria. Supone:
  - que dos `ArtMethod` contiguos tienen el tamaño de la estructura;
  - valores concretos de `kAccPublicApi` (0x10000000) y
    `kAccNterpInvokeFastPathFlag` (0x00200000).
- Si ART cambia la estructura (es un módulo mainline que se actualiza **sin cambiar de
  versión de Android**), la redirección de IO deja de instalarse y solo deja logs
  (`init jni hook error`).
- **Mitigación:** comprobación al arrancar con aviso visible, y pruebas en cada
  actualización mensual de ART.

### R8. targetSdk 28
- Hoy protege al motor de muchas restricciones que dependen del targetSdk: tipo de
  servicio en primer plano obligatorio en 14, flags de receiver, intents implícitos.
- Android 15 subió el mínimo instalable a 24. Android 17 no lo ha subido.
- Si una versión futura lo sube a 28 o más, el motor deja de instalarse y habrá que
  cumplir todas esas reglas a la vez.
- Play Protect puede marcar el APK.

### R9. Supervivencia en segundo plano
- Los stubs `:pN` no tienen un servicio en primer plano estable. `ProxyService` llama a
  `startForeground` sin tipo y el `DaemonService` va en otro proceso.
- Para recibir mensajes (FCM vía microG o GMS) hace falta un proceso vivo y exento de
  optimización de batería.
- En ColorOS, HyperOS o MagicOS hay además autoarranque y "congelación" propios.
- **Arreglo:** proceso "core" del box con servicio en primer plano
  `specialUse`/`dataSync`, guía para la exención de batería y, en Android 14+,
  `FOREGROUND_SERVICE_*` coherentes.

### R10. Límites de memoria (Android 17)
- Android 17 introduce límites de memoria por app según la RAM del dispositivo.
- **Hipótesis:** si el límite agrega todos los procesos del mismo UID, el host con
  GMS, WhatsApp y Gmail juntos puede verse limitado.
- Verificar en cuanto haya un dispositivo con Android 17.

### R11. Redirección de intents (Android 16)
- La protección por defecto se aplica cuando una app lanza un intent que va en los
  extras de otro intent recibido. Es exactamente lo que hacen `ProxyActivity` y
  `ProxyPendingActivity`.
- Medido: en el OPPO, abrir desde la notificación funcionó, así que hoy no bloquea a un
  host con target 28. Vigilar en Android 17.

### R12. Páginas de 16 KB
- `libblackbox.so` está alineada a 16 KB (verificado).
- Dobby es un `.a` precompilado de versión desconocida y no se ha probado.
- El OPPO usa páginas de 4 KB.
- Probar en un Pixel 8 o posterior con la opción de desarrollador de 16 KB.

### R13. Play Integrity
- `VirtualSpoof.cpp` hace que todo proceso que cargue `libblackbox.so` vea un Pixel 6
  con Android 12 (build `SP1A.210812.015`, parche de seguridad de enero de 2022),
  mientras el SDK real es 36.
- Esa incoherencia y el UID compartido con el host hacen improbable algo más allá de
  `MEETS_BASIC_INTEGRITY`. Con GMS virtual o microG, lo esperable es que no haya
  veredicto.
- La banca queda en "best effort", como estaba previsto.

### R14. Telemetría
- Envío automático de `logcat` a un servidor de terceros en cada fallo. Ver
  [arquitectura-motor.md](arquitectura-motor.md) §8.
- Se elimina en el fork (ADR-0003).
