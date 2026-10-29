# ADR-0005: Ampliar el alcance a Android Auto y relojes Wear OS

- Estado: aceptada (decisión del propietario); viabilidad pendiente de demostrar
- Fecha: 2026-10-29

## Contexto

El propietario quiere que Android Auto y los relojes Wear OS funcionen también en
móviles con ROM china, es decir, sin GMS en el sistema real. Hasta ahora el reloj
previsto era un Huawei Watch vía Huawei Health.

## Decisión

Android Auto y Wear OS entran en el alcance como objetivos. Cada uno lleva su propia
prueba de viabilidad antes de comprometer trabajo de producto.

## Consecuencias

Ambos dependen de que el GMS real funcione dentro del box (ADR-0004).

**Android Auto.** La app Android Auto tendría que ejecutarse dentro del box y recibir
el enlace con el coche. Está integrada en el sistema desde Android 10, y la integración
pasa por mecanismos del sistema real:
- `USB_ACCESSORY_ATTACHED` para la conexión por cable;
- Bluetooth y Wi-Fi Direct o punto de acceso para la conexión inalámbrica;
- el perfil HFP para las llamadas;
- el servicio del coche, que publica las notificaciones y la mensajería de las apps.

El host tendrá que recibir esos eventos y reenviarlos al box. Es el objetivo de mayor
incertidumbre de todo el proyecto.

**Wear OS.** La app compañera del reloj y la capa de datos de Wearable de GMS
mantienen la conexión Bluetooth. Necesitan:
- en el host, permisos de Bluetooth (`BLUETOOTH_CONNECT`/`SCAN`);
- asociación de `CompanionDeviceManager` a nombre del host;
- acceso de `NotificationListenerService` para reenviar las notificaciones.

**Smartwatch Huawei.** Se mantiene como requisito anterior, vía Huawei Health en el
sistema real.
