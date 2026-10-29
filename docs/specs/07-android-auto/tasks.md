# Spec 07: Android Auto. Tareas (spike)

- Estado: Borrador
- Fecha: 2026-10-29
- Requisitos: [requirements.md](requirements.md). Diseño: [design.md](design.md)

Presupuesto máximo del spike: 6 días de trabajo. Cada tarea cabe en un día o menos.
Las pruebas en dispositivo las ejecuta el propietario. Los APKs de Google los aporta el
usuario y no entran en el repo. Pruebas siempre con el vehículo parado.

## T07.1 Inventario estático de Android Auto
- Cubre: R07.3, R07.6.
- Qué: con el APK que aporte el usuario, listar con `aapt`/`apkanalyzer`: permisos
  (especialmente `signature`/`privileged`), servicios, filtros `USB_ACCESSORY_ATTACHED`,
  `CompanionDeviceService`, `InCallService`, `NotificationListenerService`, y si el
  APK es un stub (`AndroidAutoStub`) o la app real. Apuntar qué exige ser app de sistema.
- Verificación: sección "Inventario" en `docs/specs/07-android-auto/spike-informe.md`.
- Estimación: 0,5 día.

## T07.2 Línea base DHU en el móvil con GMS
- Cubre: R07.4.
- Qué: en el OPPO CPH2841 instalar Android Auto, activar el modo de desarrollador y el
  servidor de unidad principal, conectar el DHU desde el PC por `adb forward`.
  Probar con NotifProbe un mensaje con `MessagingStyle`.
- Verificación: captura del DHU con el mensaje (sin notificaciones personales) y `logcat`
  filtrado con `scripts/fase0/diag.sh`.
- Estimación: 0,5 día.

## T07.3 Android Auto dentro del box con DHU
- Cubre: R07.5, R07.6, R07.13, R07.17.
- Depende de: spec 03 (si no está, marcar "pendiente" y pasar a T07.7 con lo que haya).
- Qué: instalar Android Auto en el box, abrirlo, activar el servidor de unidad principal
  y conectar el DHU. Registrar cada excepción de permiso o de API del sistema y cualquier
  mensaje de integridad o de firma. Probar una app de medios de prueba del box.
- Verificación: tabla "API → resultado" y captura del DHU en Android 16.
- **Punto de decisión 1:** si no arranca por privilegios o integridad, parar (R07.2).
- Estimación: 1 día.

## T07.4 Cable: accesorio USB
- Cubre: R07.7, R07.8, R07.9.
- Depende de: T07.3 en "Continuar" o "Pivotar".
- Qué: actividad del host con el filtro `USB_ACCESSORY_ATTACHED`; `IUsbManagerProxy`
  mínimo (`openAccessory`) en `fake/service/`; entrega del descriptor a Android Auto del
  box. Segundo dispositivo Android con una app de unidad principal como emisor del
  modo accesorio, o el coche si se confirma.
- Verificación: el host registra el evento; la unidad principal alternativa o el coche
  muestra la pantalla de Android Auto del box.
- **Punto de decisión 2:** sin accesorio abierto, el cable queda abandonado.
- Estimación: 1,5 días (la más incierta).

## T07.5 Inalámbrico y llamadas
- Cubre: R07.10, R07.11, R07.12.
- Depende de: spec 06 (T06.2, proxy de Bluetooth) y T07.3.
- Qué: con el proxy de Bluetooth, observar el socket RFCOMM de Android Auto, la unión
  a la Wi-Fi de la unidad principal y la pantalla de llamadas. No arreglar, medir y
  anotar cada llamada que falla.
- Verificación: tabla de resultados por llamada; llamada de prueba por manos libres
  con el sistema real.
- Estimación: 1 día.

## T07.6 Mensajería en el coche
- Cubre: R07.14, R07.15.
- Depende de: spec 06 (T06.5) y spec 02.
- Qué: NotifProbe publica un `MessagingStyle` con acciones semánticas de respuesta y de
  marcar como leído; comprobar que Android Auto del box lo recibe por el servicio de
  escucha del box y que la respuesta de voz o simulada llega a la app. Con el puente
  real (T06.9), repetir con una notificación del sistema real.
- Verificación: captura del DHU y registro de la app de origen.
- Estimación: 0,5 día.

## T07.7 Informe y decisión
- Cubre: R07.1, R07.2, R07.18.
- Qué: rellenar la tabla de criterios de salida de [design.md](design.md); decisión
  continuar / pivotar / abandonar; sección de alternativas con costes y recomendación
  de compra; ADR si cambia el alcance; actualizar ROADMAP (hito M6).
- Verificación: `spike-informe.md` revisado por el propietario.
- Estimación: 0,5 día.

## Resumen de esfuerzo

| Parte | Días de trabajo | Comentario |
|---|---|---|
| Spike | 5,5 (máximo 6) | Cada punto de decisión puede acortarlo |
| Implementación completa | 25 a 60+ | Solo si sale "continuar"; estimación sin base, a rehacer con el informe |

Si T07.3 falla por privilegios, el coste real del spike es de unos 2 días (T07.1 a T07.3).
