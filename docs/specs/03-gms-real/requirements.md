# Spec 03: GMS, GSF y Play Store reales dentro del box. Requisitos

Estado: Borrador
Fecha: 2026-10-29
Tipo: Spike + funcionalidad
Base: [ADR-0004](../../adr/0004-gms-real-como-ruta-principal.md),
[fuentes-gms](../../investigacion/fuentes-gms.md),
[riesgos-android R2](../../fase0/riesgos-android.md),
[alcance-parches §4](../../fase0/alcance-parches.md),
[plan de pruebas](../../fase0/plan-pruebas.md) (pasos b, c, d).
Depende de: spec 01 (splits; no bloquea el spike porque se usan APK únicos) y 05.
Desbloquea: 04, 06, 07, 08.

## Objetivo

El usuario de un móvil con ROM china sin GMS (Android 16 y 17) instala dentro del box
GmsCore, GSF y Play Store reales a partir de ficheros que aporta él, inicia sesión con
una cuenta de Google y las apps invitadas reciben push (FCM) y pueden instalarse desde
Play Store. En un móvil con GMS real (OPPO) el box no debe romper ni mezclar nada con
el GMS del sistema.

## Hechos medidos (partida)

- GMS clonado del host en Android 16: bucle de crashes (`jni_android.cc:220`, SIGTRAP,
  cronet).
- Play Store clonada: `ClassNotFoundException` por splits.
- Providers de GMS, GSF y vending enviados al GMS real: `Permission Denial ...
  INTERNAL_BROADCAST`.
- `GmsCore.installGApps` solo clona del host; no instala desde fichero.

## Spike (tiempo acotado)

### R03.1 Duración y alcance del spike
THE spike SHALL durar como máximo 10 días laborables, empezando por instalar APK únicos
(MindTheGapps 16 en el dispositivo Android 16 sin GMS) y sin esperar a la spec 01.
Verificación: tareas T03.1 a T03.10 cerradas o spike cerrado por tiempo; informe en
`docs/specs/03-gms-real/spike-informe.md` (se crea al terminar el spike).

### R03.2 Evidencias del spike
THE spike SHALL registrar, para cada hito, comando, resultado y extracto de logcat
sanitizado: (a) los tres paquetes instalados en User 0; (b) GmsCore vivo 2 min con
pantalla apagada; (c) causa raíz del crash de cronet; (d) providers GMS resueltos al
GMS virtual; (e) pantalla de login de Google abierta; (f) token FCM obtenido por una
app invitada.
Verificación: tabla de hitos en el informe; plan de pruebas pasos b2 y c.

### R03.3 Criterio de salida
WHEN el spike termine THE responsable SHALL registrar una de tres decisiones:
- **Continuar:** hitos (a), (b), (d) y (e) cumplidos y (c) con causa raíz identificada
  y arreglo conocido.
- **Pivotar a microG:** hay un bloqueo duro en (b), (c) o (d) sin arreglo plausible en
  los días restantes (p. ej. GMS exige un privilegio que solo el sistema concede y que no
  se puede emular en el proceso). Se escribe un ADR y se abre la spec de microG.
- **Abandonar:** ni GMS real ni microG alcanzan (b) y (e) en Android 16.
Verificación: ADR nuevo (continuar con matices, pivotar o abandonar) enlazado desde el
informe; el ROADMAP M3 se actualiza.

### R03.4 Parada anticipada
IF al día 5 del spike el hito (b) no se cumple y no hay hipótesis nueva sobre el crash
THEN el responsable SHALL convocar al propietario para decidir pivotar antes de agotar
los 10 días.
Verificación: nota fechada en el informe.

## Instalación

### R03.5 Instalar desde ficheros en orden
WHEN el usuario elija instalar GMS desde ficheros THE host SHALL instalar en el box, en
este orden, GSF (`com.google.android.gsf`), GmsCore (`com.google.android.gms`) y Play
Store (`com.android.vending`), a partir de APK únicos seleccionados por el usuario.
Verificación: plan de pruebas b2; `pm list packages` del box muestra los tres.

### R03.6 Verificación de firma
WHEN se seleccione un fichero para estos paquetes THE host SHALL comprobar que su
certificado coincide con el de Google, y IF no coincide THEN SHALL rechazarlo con un
mensaje claro.
Verificación: test unitario con un APK de prueba firmado con otra clave; ver
[fuentes-gms](../../investigacion/fuentes-gms.md#verificar-la-firma-antes-de-instalar).

### R03.7 Android 17 desde APEX
WHERE el origen sea Android 17 THE host SHALL aceptar el APK ya extraído del APEX de
GmsCore (la extracción la hace el usuario fuera de la app, con la guía del repositorio).
Verificación: instalar en el dispositivo Android 17 el APK extraído (sin verificar:
formato exacto del APEX y si el APK extraído arranca fuera de su APEX).

### R03.8 Sin APK de Google en el repositorio ni descarga
THE host SHALL NOT incluir ni descargar APK de Google; solo los lee de la ruta que elija
el usuario.
Verificación: revisión de CI/escaneo de binarios; el `.gitignore` excluye `*.apk`
de datos locales.

## Estatus privilegiado

### R03.9 Aplicación virtual de sistema y privilegiada
WHILE un paquete sea GMS, GSF o Play Store virtual THE motor SHALL exponer su
`ApplicationInfo` con `FLAG_SYSTEM` y el indicador privilegiado, y su `PackageInfo` con
los `requestedPermissions` concedidos.
Verificación: `dumpsys`-equivalente del box o log de depuración; `getApplicationInfo`
desde una app de prueba dentro del box; plan de pruebas b2.

### R03.10 Permisos privilegiados concedidos
THE motor SHALL conceder a los paquetes de Google los permisos listados en
`privapp-permissions-google*.xml` y `default-permissions-google.xml` de MindTheGapps
(definidos como datos del motor, no leídos de la partición real).
Verificación: `checkPermission` desde GmsCore devuelve GRANTED; ausencia de
`SecurityException` y `Permission Denial` en logcat.

### R03.11 Permisos de firma entre paquetes de Google
WHEN un paquete de Google compruebe un permiso `signature` definido por otro paquete de
Google (p. ej. `INTERNAL_BROADCAST`) THE motor SHALL concederlo, y `checkSignatures` entre
ellos SHALL devolver SIGNATURE_MATCH.
Verificación: logcat sin `Permission Denial: opening provider`; plan de pruebas b.

### R03.12 Permisos que el motor no puede dar
IF GMS necesita un permiso que solo el sistema real concede (p. ej. de
`INSTALL_PACKAGES` o `READ_PRIVILEGED_PHONE_STATE`) THEN el motor SHALL responder de
forma segura (sin crash) y SHALL registrar la carencia en el informe del spike.
Verificación: lista de permisos denegados en el informe (sin verificar cuáles hay).

## Providers, servicios y proceso

### R03.13 Sin passthrough con GMS virtual
WHILE GMS esté instalado en el box THE motor SHALL resolver las authorities de GMS, GSF
y Play Store contra el GMS virtual y SHALL NOT delegarlas en el sistema real.
Verificación: logcat `grep -iE "Permission Denial: opening provider"` vacío; `bindService`
a GMS llega a un proceso `:pN` del box.

### R03.14 Passthrough solo sin GMS virtual
WHILE GMS NO esté instalado en el box y el host sí tenga GMS THE motor SHALL mantener
el comportamiento actual (clonado/passthrough) tras confirmarse el estado en el spike.
Verificación: OPPO, plan de pruebas b1 sin regresión.

### R03.15 Causa raíz del crash de cronet
THE spike SHALL identificar la causa del `jni_android.cc:220` y la spec SHALL registrar
el arreglo (hipótesis: API oculta, `.so` no cargada, proveedor de WebView/cronet,
propiedades falsas de VirtualSpoof).
Verificación: logcat sin `jni_android.cc` durante 10 min; informe con la causa.

### R03.16 Proceso persistente de GMS
WHILE el box esté activo THE motor SHALL mantener vivo el proceso de GmsCore y
reiniciarlo si muere, con el servicio en primer plano del host de la spec 04.
Verificación: proceso `:pN` de GMS vivo a los 2 min y 60 min con pantalla apagada
(plan b2, d).

### R03.17 Excepciones de GMS no silenciadas
THE motor SHALL NOT silenciar las excepciones de GMS en `SimpleCrashFix` durante el
spike (para ver los fallos reales); después SHALL registrarlas y reiniciar el proceso.
Verificación: tras T03.2, un crash de GMS aparece en logcat.

## Cuenta, push y Play Store

### R03.18 Inicio de sesión en cuenta virtual
WHEN el usuario inicie sesión con una cuenta de Google dentro del box THE motor SHALL
guardarla en el AccountManager virtual y SHALL NOT escribirla en el AccountManager del
sistema real.
Verificación: plan de pruebas c; `dumpsys account` real no muestra la cuenta.

### R03.19 Identidad de dispositivo propia
THE box SHALL obtener su propio ID de GSF/checkin y SHALL NOT reutilizar el del sistema
real.
Verificación: ID de GSF del box distinto del real (consulta con app de prueba en el box).

### R03.20 Play Store instala apps
WHEN el usuario abra Play Store en el box THE motor SHALL permitir descargar e instalar
una app y que se registre como paquete del box.
Verificación: instalar una app de prueba; aparece en User 0. (Depende del spec 01 si es
split; sin verificar el flujo de instalación de Phonesky hacia el motor, ver design.)

### R03.21 Registro FCM
WHEN una app invitada pida un token FCM THE motor SHALL entregarlo vía c2dm de GMS
virtual, y WHEN llegue un mensaje THE motor SHALL entregarlo a la app invitada.
Verificación: plan de pruebas d; logcat
`grep -E "c2dm.intent.RECEIVE|gcm.intent.RECEIVE"`; retraso < 1 min.

## Dispositivo con GMS real

### R03.22 Coexistencia
WHERE el sistema real tenga GMS THE box SHALL funcionar con su GMS virtual aislado sin
afectar al del sistema, y SHALL ofrecer también la ruta de clonado actual.
Verificación: OPPO: Play Store real intacta tras usar el box; sin cuentas cruzadas.

## Riesgos y cumplimiento

### R03.23 Aviso al usuario
THE host SHALL informar de que usar GMS en un entorno virtual puede incumplir las
condiciones de Google y puede causar la restricción de la cuenta (sin verificar la
frecuencia), y SHALL recomendar una cuenta secundaria.
Verificación: texto mostrado antes de la primera instalación.

## Fuera de alcance

Spoofing de firma / microG (solo si se pivota), Play Integrity (spec 08), Android Auto
(07), Wear OS (06), distribución de APK de Google.
