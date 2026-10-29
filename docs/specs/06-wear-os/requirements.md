# Spec 06: Wear OS. Requisitos

- Estado: Borrador
- Fecha: 2026-10-29
- Tipo: spike + funcionalidad
- Depende de: [03-gms-real](../03-gms-real/) (GMS en el box), [02-notificaciones](../02-notificaciones/)
  (acciones y respuesta), [04-segundo-plano](../04-segundo-plano/) (procesos vivos)
- Contexto: [ADR-0005](../../adr/0005-alcance-android-auto-wear-os.md), hito M5 del
  [ROADMAP](../ROADMAP.md)

## Objetivo

Que un reloj Wear OS se empareje con su app compañera ejecutada dentro del box, y que
reciba las notificaciones de las apps del box (y, opcionalmente, del sistema real) en
un móvil con ROM china sin GMS. El modelo de reloj aún no se conoce (llega pronto): los
requisitos por fabricante se cierran con el spike.

## Historias de usuario

- Como propietario, quiero emparejar mi reloj Wear OS desde el box, para usarlo en un
  móvil sin GMS.
- Como propietario, quiero ver en el reloj los mensajes de WhatsApp del box y poder
  responderlos desde la muñeca.
- Como propietario, quiero decidir qué notificaciones del sistema real llegan al reloj,
  para no exponer más de lo necesario.

## Fase A. Spike (4–5 días de trabajo, más espera del reloj)

**R06.1** WHEN termina el spike THE equipo SHALL entregar un informe con una decisión
*continuar / pivotar / abandonar* y la evidencia de cada bloqueo duro del diseño.
- Verificación: existe `docs/specs/06-wear-os/spike-informe.md` con la tabla de criterios
  de salida del [diseño](design.md) rellena (T06.7).

**R06.2** WHILE la spec 03 no tenga el GMS real estable en Android 16 THE spike SHALL
limitarse a las pruebas que no lo necesitan (T06.1 a T06.4) y marcar el resto como
pendiente.
- Verificación: el informe indica la versión de GMS usada o "pendiente de spec 03".

## Fase B. Bluetooth y asociación

**R06.3** WHEN una app invitada usa `BluetoothAdapter`, BLE (escaneo, GATT,
publicidad) o sockets RFCOMM THE motor SHALL ejecutar la llamada con la identidad del
host (paquete, UID y `AttributionSource`), de modo que el sistema real la acepte.
- Verificación: WearProbe (`tools/wearprobe`, a crear en T06.2) lista dispositivos BLE y
  abre un GATT contra un periférico de prueba desde el box en Android 16 y 17.

**R06.4** WHEN una app invitada consulta o solicita `BLUETOOTH_CONNECT`,
`BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `NEARBY_WIFI_DEVICES` o ubicación THE motor
SHALL responder según la concesión real del host y SHALL ofrecer en la UI del host el
flujo para concederlas.
- Verificación: WearProbe muestra el estado real; tras conceder desde el host, el
  escaneo funciona (T06.3).

**R06.5** IF el host no tiene concedido un permiso en el sistema real THEN THE motor
SHALL NOT informar de "concedido" a la app invitada.
- Verificación: test instrumentado que revoca el permiso y comprueba
  `checkSelfPermission` en el box.

**R06.6** WHEN una app invitada llama a `CompanionDeviceManager` (`associate`,
`getMyAssociations`, `disassociate`, `startObservingDevicePresence`) THE motor SHALL
atribuir la asociación al paquete del host y mantener un mapa
paquete invitado → asociaciones.
- Verificación: WearProbe asocia un periférico BLE; `dumpsys companiondevice` del móvil
  lo muestra bajo el paquete del host (T06.4).

**R06.7** WHEN el sistema notifica presencia de un dispositivo asociado THE motor SHALL
vincular el `CompanionDeviceService` declarado por la app invitada dentro del box.
- Verificación: WearProbe registra `onDeviceAppeared` al encender el periférico.

**R06.8** WHERE el sistema muestre diálogos propios (selector de dispositivo, PIN de
emparejamiento, permiso de ubicación) THE motor SHALL permitir que se muestren sobre la
actividad invitada sin romper su pila.
- Verificación: paso de emparejamiento completo con el reloj real (T06.8).

## Fase C. Notificaciones

**R06.9** THE motor SHALL ofrecer a las apps invitadas un servicio de escucha de
notificaciones propio del box (`NotificationListenerService`): conexión, alta y baja de
notificaciones, `getActiveNotifications`, ranking, `cancelNotification` y `snooze`, para
las notificaciones publicadas por apps del box.
- Verificación: un servicio de escucha de prueba en WearProbe recibe las notificaciones
  de NotifProbe con nombre de app, canal y acciones (T06.5).

**R06.10** WHEN una app invitada consulta si tiene acceso a notificaciones
(`isNotificationListenerAccessGranted`, `enabled_notification_listeners`) o abre
`ACTION_NOTIFICATION_LISTENER_SETTINGS` THE motor SHALL responder con el estado del box
y mostrar una pantalla propia para concederlo, sin salir al ajuste del sistema real.
- Verificación: la app compañera de prueba concede el acceso y recibe
  `onListenerConnected`.

**R06.11** WHERE el usuario active "puente del sistema real" THE host SHALL declarar un
`NotificationListenerService` real, reenviar al box las notificaciones del sistema que
el usuario haya elegido y entregarlas a los servicios de escucha invitados autorizados.
- Verificación: una notificación de una app del sistema real aparece en el servicio de
  escucha de WearProbe (T06.9).

**R06.12** THE puente del sistema real SHALL estar desactivado por defecto, filtrar por
app de origen elegida, no persistir contenido en disco, no escribirlo en logs y mostrar
en la UI del host un indicador de que está activo.
- Verificación: revisión de código y de `logcat` con una notificación de prueba (T06.9).

**R06.13** WHEN la app compañera cancela una notificación o dispara una de sus acciones
(respuesta incluida) THE motor SHALL ejecutar el `PendingIntent` y el `RemoteInput`
originales, tanto de apps del box como del sistema real.
- Verificación: respuesta enviada desde el reloj llega a la app de origen (depende de
  spec 02; T06.11).

**R06.14** WHEN una notificación se descarta en el reloj THE motor SHALL cancelarla en
su origen, y viceversa, sin bucles de eco.
- Verificación: descarte en ambos sentidos en el reloj real (T06.11).

## Fase D. Capa de datos y apps del reloj

**R06.15** WHILE un reloj está emparejado y el box tiene procesos vivos THE motor
SHALL mantener la conexión de la capa de datos de Wearable (GMS) estable, con mensajes
de la app del reloj entregados al `WearableListenerService` invitado en menos de
5 s (valor propuesto, a ajustar con el spike).
- Verificación: 60 minutos de inactividad de pantalla sin pérdida de conexión (T06.12).

**R06.16** WHEN el flujo de alta del reloj necesite la cuenta de Google THE box SHALL
exponerla a la app compañera mediante su gestor de cuentas (spec 03).
- Verificación: el alta del reloj termina con la cuenta del box.

**R06.17** THE equipo SHALL documentar qué app compañera funciona para cada fabricante
de reloj probado (Wear OS de Google, Pixel Watch, Samsung, Mobvoi, otros) y con qué
limitaciones, empezando por el modelo que llegue.
- Verificación: tabla "fabricante → app → resultado" en `spike-informe.md`.

## Transversales

**R06.18** THE funcionalidad SHALL funcionar en Android 16 y en Android 17.
- Verificación: paso del plan de pruebas repetido en ambos móviles.

**R06.19** IF una llamada de Bluetooth, asociación o escucha de notificaciones falla por
permiso o por identidad THEN THE motor SHALL registrar un evento estructurado (clase,
método, causa) sin direcciones MAC completas ni contenido de notificaciones.
- Verificación: revisión de `logcat` en los casos de fallo de T06.2 a T06.5.

## Fuera de alcance

- Reimplementar el protocolo de la capa de datos o la app compañera.
- Relojes sin Wear OS (Huawei: spec 02).
- Funciones del reloj que dependan de servicios de Google no incluidos en el GMS del box
  (pagos, Asistente).
