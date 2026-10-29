# Fase 0: plan de pruebas en dispositivo físico

Lo ejecuta el propietario. Para cada paso: qué hacer, el criterio de éxito, qué se
espera con el upstream sin parches (según [resultados-dispositivo.md](resultados-dispositivo.md)
y [riesgos-android.md](riesgos-android.md)) y cómo diagnosticar.

Repite el plan en cada build del fork. La columna "esperado upstream" sirve para no
perder tiempo en pasos que ya sabemos que fallan.

## 0. Preparación

```bash
# Variables de la sesión
export HOST_PKG=top.niunaijun.blackbox
mkdir -p ~/fase0-logs

# Datos del dispositivo y del entorno (no subir al repo: pueden contener datos personales)
scripts/fase0/diag.sh ~/fase0-logs/00-inicial
```

Anota en el informe:
- Modelo, ROM, Android y parche: salen en `device.txt`.
- ¿El sistema real tiene GMS? Decide la ruta del paso b.
- Tamaño de página: `getconf PAGE_SIZE`, que debe dar 4096 o 16384.

Filtro de logcat común, en una terminal aparte durante todo el plan:

```bash
adb logcat -c
adb logcat -v threadtime | tee ~/fase0-logs/logcat-completo.txt | \
  grep -E --line-buffered "NativeCore|BlackBoxCore|BActivityThread|BPackageManagerService|HCallbackStub|INotificationManagerProxy|AndroidRuntime|FATAL|has died|ANR in|Permission Denial|hiddenapi:.*denied|Abort message"
```

Recuerda:
- Los `Log.i` de las apps invitadas **no aparecen** en logcat. Usa `dumpsys` y los
  efectos visibles.
- Cada proceso `top.niunaijun.blackbox:pN` es una app invitada. `:black` es el
  "sistema" virtual.

```bash
adb shell ps -A -o PID,NAME | grep $HOST_PKG                 # qué procesos hay vivos
adb shell dumpsys activity processes $HOST_PKG | grep -E "ProcessRecord|pid="
```

---

## a. Instalar el motor en Android 16

| | |
|---|---|
| Acción | `adb install -r -g BlackBox_*_arm64-v8a-*.apk`. Abrir la app. Denegar la VPN. Conceder "All files access" si vas a usar almacenamiento compartido dentro |
| Éxito | Se ve la pantalla "User 0", existe el proceso `:black` y el log incluye `BlackBox system initialized successfully` |
| Esperado upstream | **OK** (medido en el OPPO con Android 16) |

Diagnóstico:

```bash
adb shell dumpsys package $HOST_PKG | grep -E "versionName|targetSdk"
adb logcat -d | grep -E "HiddenAPI|init jni hook error|jni hook error|SystemCallProvider"
```

- `init jni hook error. art_method_*_offset not found!` significa que la redirección de
  IO no está activa. Es un fallo bloqueante (R7).
- `HiddenAPI: Didn't find setHiddenApiExemptions` es esperado en Android 16 y no
  bloquea por sí solo (R5).

---

## b. Instalar GMS (y, como alternativa, microG) dentro

Elige la ruta según el sistema real.

**b1. El sistema real tiene GMS:** Ajustes de BlackBox → GMS Manager → activar "User 0".

**b2. ROM sin GMS, ruta GMS.** Instalar desde archivo, dentro del box:
1. `com.google.android.gsf`
2. `com.google.android.gms`
3. `com.android.vending`

Los APK los consigues tú. Nunca van al repo.

**b3. microG.** Instalar desde archivo, dentro del box:
1. `com.google.android.gms` de microG (GmsCore)
2. `com.android.vending` de microG (Companion)
3. opcionalmente GsfProxy

| | |
|---|---|
| Éxito | Las apps aparecen en "User 0". Al abrir "Servicios de Google Play" (o "microG Settings") no hay crash en 60 s, y su proceso `:pN` sigue vivo a los 2 min con la pantalla apagada |
| Esperado upstream | b1 se instala pero **GMS crashea en bucle** y **Play Store no abre** por los splits (medido). b2 **falla**: GMS y Play Store son bundles de splits. b3: microG se instala si lo bajas como APK único, pero **sin signature spoofing** las apps no lo aceptan como GMS |
| Arreglo necesario | Soporte de splits (R1), sin passthrough de providers GMS y spoofing de firma para microG (R2) |

Diagnóstico:

```bash
adb shell pm path com.google.android.gms | wc -l              # >1 ⇒ el host lo tiene en splits
adb logcat -d | grep -E "ClassNotFoundException|Unable to instantiate|Permission Denial: opening provider|jni_android.cc|has died"
# microG: autodiagnóstico
#   microG Settings → Self-Check → "System grants signature spoofing permission"
#   y "System spoofs signature" deben salir marcados
```

---

## c. Iniciar sesión con una cuenta de Google

| | |
|---|---|
| Acción | Dentro del box: Ajustes del sistema virtual → Cuentas, o abrir Play Store y "Iniciar sesión". Con microG: microG Settings → Cuentas |
| Éxito | La cuenta aparece en el AccountManager **virtual** y **no** en el sistema real. Play Store (o microG) muestra la cuenta. Se puede usar una cuenta distinta de la del sistema real |
| Esperado upstream | **Falla**: depende de b |

Diagnóstico:

```bash
adb shell dumpsys account | grep -A3 "Accounts:"   # cuentas del sistema REAL: la nueva NO debe aparecer
adb logcat -d | grep -iE "AccountManagerService|GoogleAuthUtil|DroidGuard|BadAuthentication|NeedPermission"
```

---

## d. Instalar WhatsApp y Gmail dentro y recibir mensajes

| | |
|---|---|
| Acción | Instalar WhatsApp (mejor el APK universal de whatsapp.com, que es un único APK) y Gmail. Registrar WhatsApp con un número **distinto** del de fuera, o con el mismo número si fuera no está registrado. Enviarle mensajes con el móvil bloqueado y la app cerrada; probar a los 1, 10 y 60 minutos |
| Éxito | Los mensajes llegan con la pantalla apagada sin abrir la app; el retraso medio es inferior a 1 minuto a los 10 y 60 minutos |
| Esperado upstream | **Falla**. Sin GMS o microG operativos no hay FCM. WhatsApp clonado del sistema arranca (medido), pero Gmail y Play Store clonados necesitan splits |
| Arreglo necesario | R1, R2 y R9 (proceso vivo y exento de batería) |

Diagnóstico:

```bash
adb shell dumpsys deviceidle whitelist | grep $HOST_PKG   # debe aparecer tras eximir la batería
adb shell dumpsys activity broadcasts | grep -B2 -A5 "c2dm.intent.RECEIVE"
adb logcat -d | grep -iE "GCM|FCM|c2dm|mtalk|Heartbeat"
# Batería sin restricciones (ColorOS: Ajustes > Batería > BlackBox > Permitir actividad en segundo plano)
adb shell cmd appops get $HOST_PKG RUN_ANY_IN_BACKGROUND
```

---

## e. Notificación visible fuera, con el nombre de la app invitada

| | |
|---|---|
| Acción | Recibir un mensaje en WhatsApp del box. Para un resultado reproducible sin cuentas, usar **NotifProbe** (`tools/notifprobe`) instalado dentro del box |
| Éxito | Aparece en la bandeja del sistema real; muestra de forma visible el nombre de la app invitada **y** el perfil (p. ej. "WhatsApp · Perfil 1") en título, subtexto o icono grande; el icono es el de la app; dos notificaciones con distinto tag no se pisan |
| Esperado upstream | **Parcial**: llega, pero con la cabecera "BlackBox", sin app ni perfil, con icono incorrecto y pisando tags (medido) |
| Nota | El nombre de la cabecera **no se puede cambiar** sin permisos de sistema. El criterio de éxito es el subtexto, el icono grande y el canal |

Diagnóstico:

```bash
adb shell dumpsys notification --noredact > ~/fase0-logs/notif.txt
grep -E "NotificationRecord\(.*$HOST_PKG|icon=|android.title=|android.subText=|channel=" ~/fase0-logs/notif.txt
adb shell cmd notification list | grep $HOST_PKG
```

---

## f. Responder desde la notificación

| | |
|---|---|
| Acción | Desplegar la notificación → "Responder" → escribir → Enviar. En NotifProbe probar `Resp(BR)` y `Resp(SVC)` |
| Éxito | El mensaje se envía desde la cuenta **del box** (compruébalo en el otro extremo). La notificación deja de mostrar el spinner. NotifProbe publica `REPLY_BR_OK text=...` y `REPLY_SVC_OK text=...`. **Nunca** debe abrirse ni recibir nada la app real de fuera |
| Esperado upstream | **Falla** en ambos casos (medido) |
| Arreglo necesario | R3 |

Diagnóstico:

```bash
adb logcat -d | grep -E "Unable to start service|Unable to start receiver|ProxyBroadcastReceiver|ProxyService|BroadcastQueue.*$HOST_PKG"
adb shell dumpsys notification --noredact | grep "android.title=" | grep -E "REPLY_|CONTENT_INTENT_OK"
```

---

## g. Notificación reenviada al reloj (Huawei Watch + Huawei Health)

| | |
|---|---|
| Acción | Huawei Health → dispositivo → Notificaciones → activar **BlackBox**. Recibir un mensaje en WhatsApp del box con el móvil bloqueado |
| Éxito | El reloj vibra y muestra el mensaje. El texto o el subtexto permite saber que viene de WhatsApp del perfil N. Si el reloj ofrece respuesta rápida, responder y verificar como en f |
| Esperado upstream | Llega como "BlackBox" (hipótesis). La respuesta desde el reloj depende de f, así que **falla** |
| Nota | Huawei Health filtra por **paquete emisor**, así que no se puede activar o desactivar por app invitada. Si es imprescindible, habría que plantear una app satélite por perfil, que es una decisión de diseño |

Diagnóstico:

```bash
adb shell dumpsys notification --noredact | grep -E "NotificationListeners|com.huawei.health" | head
adb shell cmd notification allowed_listeners | grep -i huawei
adb logcat -d | grep -iE "com.huawei.health|NotificationListener" | tail -50
```

---

## h. Nivel de Play Integrity dentro

| | |
|---|---|
| Acción | Instalar dentro del box una app comprobadora de Play Integrity (por ejemplo "Play Integrity API Checker"; la eliges tú) y ejecutarla. Repetir fuera del box como referencia |
| Éxito | El informe registra el veredicto: `MEETS_BASIC_INTEGRITY`, `MEETS_DEVICE_INTEGRITY` o `MEETS_STRONG_INTEGRITY`, y el `appRecognitionVerdict`. No hay umbral de éxito: la banca es "best effort". Se considera **aceptable** `MEETS_BASIC_INTEGRITY` |
| Esperado upstream | Sin veredicto. La API vive en Play Store y GMS, que no funcionan (b). Con ellos funcionando, lo previsible es como mucho BASIC: el fingerprint falso de Pixel 6 con Android 12 y el UID del host no cuadran (R13) |

Diagnóstico:

```bash
adb logcat -d | grep -iE "IntegrityService|StandardIntegrity|ExpressIntegrity|DroidGuard|PlayCore"
```

---

## Informe que necesito de vuelta

Por cada paso:
- Resultado: OK, parcial o falla.
- Hora aproximada.
- La carpeta generada por `scripts/fase0/diag.sh ~/fase0-logs/<paso>` justo después.

**Revisa los ficheros antes de pasármelos.** `notification.txt` y `logcat.txt` contienen
el texto de tus notificaciones y mensajes.
