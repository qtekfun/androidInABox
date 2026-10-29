# Spec 07: Android Auto. Diseño del spike

- Estado: Borrador
- Fecha: 2026-10-29
- Requisitos: [requirements.md](requirements.md)
- Rutas relativas a `engine/`; código del motor en
  `Bcore/src/main/java/top/niunaijun/blackbox/` (abreviado `<bb>/`).

## 1. Fuentes consultadas

- Simulador de unidad principal (DHU), sirve para probar sin coche:
  https://developer.android.com/training/cars/testing/dhu
- Cable: modo accesorio USB y `USB_ACCESSORY_ATTACHED`, permiso y `openAccessory`:
  https://developer.android.com/develop/connectivity/usb/accessory
- Mensajería en el coche: `MessagingStyle` con acciones semánticas de respuesta y de
  marcar como leído, que Android Auto detecta al recibir el evento de notificación
  nueva: https://developer.android.com/training/cars/communication/notification-messaging
- Requisitos de Android Auto inalámbrico (Android 11 o posterior, Wi-Fi de 5 GHz,
  Bluetooth para el primer enlace y el perfil de manos libres):
  https://www.aawireless.io/en/articles/how-to-make-android-auto-wireless
  (fabricante de adaptadores; los requisitos de Android 11 y 5 GHz aparecen en varias
  guías no oficiales, sin verificar contra la documentación de Google).
- Android Auto sin GMS de Google, con microG: guía de la comunidad /e/OS (abril de
  2025): funciona con ROM que ya traen microG, falsificación de firma y el módulo de
  Android Auto instalado como **app de sistema**, más "stubs" de Google, Maps y TTS; las
  apps de navegación de terceros exigen figurar como instaladas por Play:
  https://community.e.foundation/t/guide-microg-android-auto-without-root-with-navigation-apps-working/69346
  Contraste: en 2018 se afirmaba que no funcionaba sin Play services
  (https://www.reddit.com/r/AndroidAuto/comments/9hygdo/does_android_auto_work_without_play_services/).
- Paquetes de GApps: MindTheGapps incluye `AndroidAutoStub.apk`, un stub; la app real
  se baja de Play ([fuentes-gms.md](../../investigacion/fuentes-gms.md)).
- Notificaciones en Android Automotive OS (otro producto; sirve como referencia de los
  requisitos de `MessagingStyle`, no de Android Auto del móvil):
  https://developer.android.com/training/cars/platforms/automotive-os/notifications

## 2. Qué es lo que hay que lograr y por qué es difícil

Android Auto en el móvil (paquete `com.google.android.projection.gearhead`, sin
verificar) hace tres cosas:
1. **Enlace con la unidad principal:** USB en modo accesorio, o Bluetooth más Wi-Fi.
2. **Proyección:** codifica vídeo y audio y recibe toques, a través de GMS (módulo de
   coche, sin verificar).
3. **Integración con el sistema:** notificaciones, llamadas, modo coche, permisos.

La evidencia de /e/OS sugiere que Android Auto se **instala como app de sistema** y
depende de que GMS (real o microG) haya sido adaptado. Dentro del box, una app invitada
nunca es de sistema ni tiene privilegios `signature`. Es la razón principal por la que
el pronóstico es bajo. Dentro del box se suman: UID compartido, `targetSdk` 28 del host y
GMS que sigue siendo un spike (spec 03).

**Android Auto en el sistema real queda descartado:** necesita GMS en el sistema real
y las ROM chinas no lo tienen. Sin esa condición no hay nada que reenviar.

## 3. Estado del motor (lectura del código)

- Sin proxy de USB, Bluetooth ni `CompanionDeviceManager` (`<bb>/fake/service/`).
- Sí hay proxies de Wifi (`IWifiManagerProxy`, `IWifiScannerProxy`), `IConnectivityManagerProxy`,
  `IMediaSessionManagerProxy`, `ITelephonyManagerProxy` y `INotificationManagerProxy`.
  Ninguno está pensado para este uso (sin verificar).
- Manifiesto del host: declara `android.hardware.usb.host` como permiso (incorrecto, es una
  característica); no declara el filtro `USB_ACCESSORY_ATTACHED` ni el servicio de
  escucha de notificaciones. Declara permisos de Bluetooth y `NEARBY_WIFI_DEVICES`.
- La virtualización de paquetes (`core/system/pm`) permite que Android Auto del box vea
  solo las apps del box como candidatas a mostrarse en el coche.

## 4. Orden del spike (de lo más barato a lo más caro)

```
 T07.1 inventario  ->  T07.2 línea base DHU en móvil con GMS
        |
        v  (requiere spec 03)
 T07.3 Android Auto en el box + DHU   <-- primer punto de decisión
        |
        +--> T07.4 cable (USB)      <-- segundo punto
        +--> T07.5 inalámbrico y llamadas
        +--> T07.6 mensajería (usa spec 06)
        v
 T07.7 informe
```

### 4.1 DHU (sin hardware)

El DHU se conecta al móvil por la red a través de `adb forward` tras activar el servidor
de unidad principal en las opciones de desarrollador de Android Auto (según la
documentación del DHU). Evita USB, Bluetooth y Wi-Fi. Si Android Auto no puede ni siquiera
mostrar el DHU dentro del box, no hay que seguir. Si lo logra, queda aislado el resto de
dificultades (enlace físico).

### 4.2 Cable

El flujo del sistema real: la unidad principal (host USB) envía las peticiones de
modo accesorio; el móvil cambia de modo; el sistema emite `USB_ACCESSORY_ATTACHED` al
paquete cuyo filtro encaje. Para el box:
1. El host declara una actividad con el filtro y `accessory_filter.xml` equivalente al de
   Android Auto (fabricante y modelo, sin verificar).
2. Al recibir el evento, el host pide el permiso y abre el accesorio con `UsbManager`.
3. Pasa el `ParcelFileDescriptor` a Android Auto del box por binder (mismo UID, válido).
4. `IUsbManagerProxy` (nuevo) responde a `getAccessoryList`, `hasAccessoryPermission` y
   `openAccessory` en el box con el accesorio del host.

Incógnitas: si Android Auto del box espera recibir el intent por sí mismo (se resuelve
reenviándolo como `ProxyActivity`); si pide `MANAGE_USB`/permisos de sistema; si el sistema
recuerda "abrir siempre con" para el host.

### 4.3 Inalámbrico

Según las fuentes, el primer enlace es Bluetooth (RFCOMM): el móvil recibe las credenciales
Wi-Fi de la unidad y luego se une a su red; el audio de llamadas sigue por el perfil
de manos libres. Necesita:
- Proxy de Bluetooth de la spec 06 (T06.2), incluido el socket servidor RFCOMM con un UUID
  concreto (sin verificar).
- Unión a la Wi-Fi de la unidad principal sin pedir confirmación cada vez. Con un
  `WifiNetworkSpecifier` el sistema pide confirmación; la API silenciosa suele requerir
  privilegios (sin verificar).
- Wi-Fi simultáneo (red de datos y red del coche) según el hardware del móvil (sin verificar).

Se mide qué llamadas fallan y con qué excepción, no se intenta arreglar todo.

### 4.4 Llamadas

Las llamadas por manos libres funcionan a nivel de sistema real: el Bluetooth del móvil
las enlaza con el coche sin que intervenga Android Auto (comportamiento clásico de
Bluetooth, sin verificar en la ROM china). Lo que hay que averiguar es si Android Auto
exige además ser el servicio de llamadas de Android (`InCallService`, solo para la app de
teléfono predeterminada) para mostrar la pantalla de llamada en el coche.

### 4.5 Mensajería

Android Auto detecta los `MessagingStyle` con acciones semánticas desde el servicio de
escucha de notificaciones. Si Android Auto corre dentro del box, el servicio de escucha
de la spec 06 (R06.9) le entrega las notificaciones del box. Para ver las del sistema real
(WeChat, SMS) hace falta el puente real (R06.11). Coste aproximado: 0 días extra de
código si la spec 06 se ha hecho; el requisito para que funcione es que NotifProbe
publique el estilo correcto (spec 02).

## 5. Piezas nuevas si el spike sale bien (no se implementan en este spike salvo lo mínimo)

| Pieza | Descripción | Mínimo del spike |
|---|---|---|
| `IUsbManagerProxy` | `getAccessoryList`, permisos, `openAccessory`, entrega de fd | Solo `openAccessory` |
| Actividad USB del host | Filtro `USB_ACCESSORY_ATTACHED` y reenvío | Sí |
| Proxy Bluetooth | De spec 06 | Reutilizado |
| Proxy de CDM | De spec 06 (Android Auto lo usa para asociar la unidad, sin verificar) | Reutilizado |
| Escucha de notificaciones | De spec 06 | Reutilizado |
| Proxy de `UiModeManager` (modo coche) | Reenviar `enableCarMode` | Solo si falla |

## 6. Criterios de salida

| Paso | Continuar | Pivotar | Abandonar |
|---|---|---|---|
| T07.3 DHU | Proyecta y responde | Proyecta con parches de privilegios | No arranca por privilegios o integridad |
| T07.4 Cable | La unidad principal alternativa o el coche muestran la pantalla | Funciona solo en modo de desarrollo | No abre el accesorio |
| T07.5 Inalámbrico | Enlace y Wi-Fi sin pasos manuales | Con pasos manuales | No hay enlace |
| T07.6 Mensajería | Aparece y responde | Solo con DHU | No llega |

Regla de decisión:
- **Continuar:** T07.3 y T07.4 en "Continuar". Se abre spec de implementación (cable
  primero, inalámbrico después).
- **Pivotar:** T07.3 funciona pero T07.4/T07.5 no. Se documenta como "solo DHU/desarrollo"
  y se descarta como función de producto.
- **Abandonar:** T07.3 no pasa. Se cierra la spec con el informe y se recomienda una
  alternativa (sección 7).

Probabilidad estimada (opinión del autor, sin verificar): éxito en cable, 10 a 20 %;
inalámbrico, 5 a 10 %; solo DHU, 25 a 35 %.

## 7. Alternativas si es inviable

| Alternativa | Qué ofrece | Coste/limitación |
|---|---|---|
| **Bluetooth clásico del sistema real** (manos libres, A2DP, AVRCP) | Llamadas, música y controles sin Android Auto y sin GMS | Sin pantalla de proyección; SMS por MAP solo si el coche lo soporta (sin verificar) |
| **Adaptador o caja con Android propio** (tipo "caja Android" de unidad principal) | Android Auto/CarPlay emulado sobre su propio sistema con GMS | Compra; la app corre en el adaptador, no en el móvil; apps del móvil solo vía espejo (sin verificar) |
| **Móvil secundario con GMS como "móvil del coche"** | Android Auto nativo | Comprar o reutilizar un móvil barato con GMS; notificaciones por reenvío (spec 06 puente) |
| **Aplicación de código abierto de unidad principal** | Depuración, no producto | No resuelve el lado móvil |
| **ROM con GMS o microG con Android Auto del sistema** | Camino conocido por la comunidad (/e/OS) | Cambiar de ROM; es otro proyecto, fuera del alcance |
| **Mensajes al reloj/pantalla propia** | Spec 06 | No es Android Auto |

La opción recomendable por coste/riesgo sin compra es la primera (Bluetooth clásico);
con compra, el móvil secundario con GMS.

## 8. Riesgos del spike

- **Alta:** Android Auto exige privilegios de sistema o verifica integridad (spec 08):
  bloquea el DHU. Es el motivo por el que el DHU se prueba primero.
- **Alta:** dependencia de spec 03 (GMS real estable) para cualquier paso dentro del box.
- **Media:** el coche o la unidad principal no están confirmados. Mitigación: DHU y
  segundo dispositivo (R07.9).
- **Media:** versiones de Android Auto con comprobaciones nuevas que cambian entre el
  momento de la prueba y el del producto.
- **Baja:** riesgo legal/seguridad: las pruebas son siempre con vehículo parado (R07.16).

## 9. Plan de pruebas

| Prueba | Requisitos | Dónde |
|---|---|---|
| Inventario estático de Android Auto | R07.6 | PC |
| DHU contra Android Auto fuera del box | R07.4 | OPPO CPH2841 |
| DHU contra Android Auto del box | R07.5, R07.6, R07.13 | Móviles con Android 16 y 17 |
| Accesorio USB | R07.7 a R07.9 | Segundo dispositivo o coche (por confirmar) |
| Bluetooth y Wi-Fi | R07.10 a R07.12 | Coche o unidad alternativa |
| NotifProbe → Android Auto | R07.14, R07.15 | DHU |
| Registro de errores de integridad | R07.17 | `scripts/fase0/diag.sh` |
