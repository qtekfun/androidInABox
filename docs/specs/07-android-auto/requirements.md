# Spec 07: Android Auto. Requisitos (spike)

- Estado: Borrador
- Fecha: 2026-10-29
- Tipo: solo spike. Si el resultado es "continuar", se abre una spec nueva de
  implementación.
- Depende de: [03-gms-real](../03-gms-real/), [02-notificaciones](../02-notificaciones/);
  comparte con [06-wear-os](../06-wear-os/) el proxy de Bluetooth (R06.3) y el servicio
  de escucha de notificaciones del box (R06.9)
- Contexto: [ADR-0005](../../adr/0005-alcance-android-auto-wear-os.md), hito M6

## Objetivo

Averiguar, con evidencias y en un máximo de 6 días de trabajo, si Android Auto puede
ejecutarse dentro del box en un móvil con ROM china sin GMS, y qué parte del
funcionamiento (cable, inalámbrico, llamadas, mensajería) es alcanzable. Es el objetivo
de mayor incertidumbre del proyecto: el resultado más probable es "pivotar o abandonar"
y debe documentarse con la misma seriedad que un "continuar".

## Historias de usuario

- Como propietario, quiero saber si puedo usar Android Auto con mi móvil de ROM china,
  para decidir si compro hardware o cambio de móvil.
- Como propietario, quiero ver y responder mensajes de apps del box en la pantalla del
  coche, si es viable.

## Requisitos del spike

**R07.1** THE spike SHALL durar como máximo 6 días de trabajo y terminar en un informe
con la decisión *continuar / pivotar / abandonar* y las evidencias de cada paso.
- Verificación: existe `docs/specs/07-android-auto/spike-informe.md` con la tabla de
  criterios de [design.md](design.md) rellena (T07.7).

**R07.2** WHEN un paso del spike falla por una causa que el diseño marca como bloqueo
duro THE equipo SHALL detener el spike, anotar la causa con su evidencia y no invertir
tiempo en los pasos siguientes que dependan de él.
- Verificación: el informe indica el paso de parada y los pasos omitidos.

**R07.3** WHILE la spec 03 no tenga el GMS real estable THE spike SHALL limitarse al
inventario estático (T07.1) y a la línea base en un móvil con GMS (T07.2).
- Verificación: el informe marca los pasos T07.3 a T07.6 como "pendiente de spec 03".

**R07.4** THE spike SHALL partir de una línea base en un móvil con GMS y Android Auto
funcionando fuera del box (OPPO CPH2841), usando el simulador de unidad principal (DHU)
para no depender de un coche.
- Verificación: capturas o logs de la sesión DHU en el móvil de referencia (T07.2).

**R07.5** WHEN Android Auto se ejecuta dentro del box THE spike SHALL comprobar si arranca,
llega al estado de espera de unidad principal y proyecta contra el DHU sin intervención
del sistema real.
- Verificación: sesión DHU contra Android Auto del box en Android 16 (T07.3).

**R07.6** WHEN el DHU funciona THE spike SHALL comprobar los permisos y APIs de sistema
que Android Auto pide y que el box no puede dar (modo coche, servicio de llamadas, red
Wi-Fi, privilegios), y listar cada fallo con su excepción.
- Verificación: tabla de "API → resultado" en el informe (T07.3).

### Cable

**R07.7** WHEN el sistema real detecta una unidad principal que entra en modo accesorio
(`USB_ACCESSORY_ATTACHED`) THE host SHALL recibir el evento y obtener el descriptor de
fichero del accesorio.
- Verificación: actividad de prueba del host que registra el evento y abre el accesorio
  (T07.4).

**R07.8** WHEN el host tiene el accesorio THE motor SHALL entregarlo a Android Auto del
box (`UsbManager.getAccessoryList`, `hasPermission`, `openAccessory`) con identidad del
host.
- Verificación: Android Auto del box abre el accesorio y la unidad principal muestra su
  pantalla (T07.4).

**R07.9** WHERE no haya coche disponible THE spike SHALL usar un segundo dispositivo
Android que actúe como unidad principal por cable (aplicación de código abierto de
unidad principal, sin verificar su compatibilidad actual).
- Verificación: el informe indica el dispositivo y la app usados, o "no disponible".

### Inalámbrico

**R07.10** WHEN se prueba el modo inalámbrico THE spike SHALL medir qué parte necesita
privilegios que el box no tiene: sockets RFCOMM, el perfil de manos libres, unión a la red
Wi-Fi de la unidad principal, Wi-Fi Direct o punto de acceso.
- Verificación: tabla de resultados por llamada, en Android 16 (T07.5).

**R07.11** IF la unión a la Wi-Fi de la unidad principal exige una API que solo concede
el sistema o una confirmación del usuario en cada conexión THEN THE spike SHALL
documentar si la experiencia resultante es aceptable.
- Verificación: nota en el informe con los pasos manuales necesarios.

### Llamadas y medios

**R07.12** THE spike SHALL comprobar si las llamadas desde el coche funcionan solo con el
Bluetooth del sistema real (perfil de manos libres) sin pasar por el box, y qué parte
de la pantalla de llamadas de Android Auto exige el servicio de llamadas del sistema.
- Verificación: llamada de prueba con el DHU o la unidad principal alternativa (T07.5).

**R07.13** WHEN Android Auto del box consulta las apps con soporte de coche THE motor
SHALL devolver las apps del box compatibles, y SHALL NOT alterar las comprobaciones de
Android Auto sobre qué categorías de app se muestran.
- Verificación: una app de medios de prueba del box aparece en el DHU (T07.3).

### Mensajería

**R07.14** WHEN una app del box publica una notificación con `MessagingStyle`, acción de
respuesta y acción de marcar como leído (semánticas) THE spike SHALL comprobar que
Android Auto del box la recibe por el servicio de escucha del box (R06.9) y que la
respuesta de voz llega a la app de origen.
- Verificación: NotifProbe publica el mensaje; el DHU lo muestra y la respuesta se
  registra en la app de origen (T07.6).

**R07.15** WHERE el usuario active el puente del sistema real (R06.11) THE spike SHALL
comprobar si los mensajes de apps del sistema real también aparecen en el coche.
- Verificación: nota en el informe; depende de spec 06.

### Límites y seguridad

**R07.16** THE spike SHALL NOT eludir ni modificar los controles de distracción al
conducir ni las listas de apps permitidas de Android Auto, ni hacer pruebas en
movimiento.
- Verificación: revisión del informe; las pruebas son con el vehículo parado o con DHU.

**R07.17** THE spike SHALL registrar si Android Auto del box rechaza ejecutarse por
comprobaciones de integridad o de firma, y pasar el dato a la spec 08.
- Verificación: apartado en el informe con el mensaje exacto de error.

**R07.18** THE informe SHALL incluir, aunque el resultado sea "abandonar", las
alternativas con su coste (ver [design.md](design.md), sección 7) y una recomendación de
compra o cambio de plan para el propietario.
- Verificación: el informe contiene la sección de alternativas.
