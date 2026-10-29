# Spec 03: GMS real dentro del box. Tareas

Estado: Borrador
Fecha: 2026-10-29
Requisitos: [requirements.md](requirements.md). Diseño: [design.md](design.md).
Estimación en días de un desarrollador (cada tarea ≤ 1 día).

## Fase A. Spike (10 días laborables, tope duro)

Dispositivo: Android 16 sin GMS. Fuente: MindTheGapps 16 (APK únicos). Sin esperar a la spec 01.

| ID | Tarea | Req. | Verificación | Días |
|---|---|---|---|---|
| T03.1 | Obtener los tres APK de MindTheGapps 16 y los XML (`privapp-permissions-google-product.xml`, `default-permissions-google.xml`, `sysconfig/google.xml`) en carpeta local fuera del repo; verificar firma con `apksigner` | R03.5, R03.6 | Huella coincide con la de Google | 0,5 |
| T03.2 | Interruptor de depuración que desactiva `SimpleCrashFix.isGooglePlayServicesCrash` y los saltos de servicios GMS en `BActivityThread` (~215-310) | R03.17 | Un crash de GMS se ve en logcat | 0,5 |
| T03.3 | Instalar GSF, GMS y Play Store desde archivo (instalación actual) y capturar el estado base: crash, `Permission Denial`, procesos | R03.2, R03.5 | Plan de pruebas b2: logcat guardado | 1 |
| T03.4 | Tabla `GooglePrivappPermissions` generada con un script desde los XML; `checkPermission`/`checkSelfPermission` la consultan | R03.10, R03.11 | `grep "Permission Denial"` sin entradas de permisos de la tabla | 1 |
| T03.5 | `FLAG_PRIVILEGED_VIRTUAL`: `FLAG_SYSTEM`, privileged y permisos concedidos en `PackageManagerCompat` | R03.9 | App de prueba en el box lee flags de GMS | 1 |
| T03.6 | Regla de providers por userId (`GmsCore.isVirtualAuthority`) y revisión de `bindService`/`startService` | R03.13, R03.14 | `grep "Permission Denial: opening provider"` vacío | 1 |
| T03.7 | Capturar y clasificar la excepción Java previa al SIGTRAP de cronet; probar H1-H6 (design §5) | R03.15 | Informe con causa y evidencia | 1 |
| T03.8 | Arreglo de la causa de T03.7 (o confirmar que H5 desaparece con APK único) | R03.15, R03.16 | GMS vivo 10 min, sin `jni_android.cc` | 1 |
| T03.9 | Abrir la pantalla de login de Google en el box, intentar añadir una cuenta secundaria | R03.18 | Plan c: llega a la pantalla o falla con mensaje de DroidGuard/auth; logcat guardado | 1 |
| T03.10 | Informe del spike, decisión continuar/pivotar/abandonar (R03.3) y ADR | R03.1-R03.4 | Documento y ADR; ROADMAP M3 actualizado | 1 |

Regla de parada: si al terminar T03.7 (día 6 aprox.) no hay hipótesis nueva, aplicar R03.4.
Suma: 9 días + 1 de margen = 10.

Resultado esperado del spike: tabla de hitos (a)-(f) de R03.2 marcados cumplido/no.
Si "pivotar": el resto de la fase B se sustituye por la spec de microG (8-12 días,
[alcance-parches §4](../../fase0/alcance-parches.md)).

## Fase B. Funcionalidad (solo si el spike dice "continuar")

| ID | Tarea | Req. | Verificación | Días |
|---|---|---|---|---|
| T03.11 | `GmsCore.installFromFiles` en orden con comprobación de firma y rollback | R03.5, R03.6 | Test unitario con APK de otra firma (rechazo) | 1 |
| T03.12 | Pantalla del host: seleccionar tres ficheros, avisos legales y de espacio; sin descargas | R03.8, R03.23 | Revisión de texto; sin permisos de red nuevos | 1 |
| T03.13 | Retirar `GoogleAccountManagerProxy` (mock) y `GmsProxy` si se confirma que son código muerto, con GMS virtual | R03.18 | `dumpsys account` real intacto | 0,5 |
| T03.14 | Autenticador `com.google` en `RegisteredServicesParser`; `getAuthToken` hasta GoogleLoginService | R03.18 | Plan c completo | 1 |
| T03.15 | Aislamiento de cuentas: auditar `AccountManagerCompat`, `TokenCache` y rutas hacia el sistema real | R03.18 | Cuenta de prueba solo en el box | 1 |
| T03.16 | DroidGuard: diagnosticar fallo y arreglo (propiedades, módulo, archivo) | R03.18 | Login completo | 1 |
| T03.17 | ID de GSF propio y revisión del checkin | R03.19 | ID distinto del real | 0,5 |
| T03.18 | Proceso persistente de GMS con reinicio; contar procesos `:pN` de GMS | R03.16 | Vivo 2 y 60 min (plan b2 y d) | 1 |
| T03.19 | Registro FCM de una app de prueba (NotifProbe) | R03.21 | Token obtenido; `c2dm` en logcat | 1 |
| T03.20 | Entrega de mensajes FCM a guest tras inactividad 1/10/60 min | R03.21 | Plan d con NotifProbe | 1 |
| T03.21 | WhatsApp y Gmail reciben mensajes con pantalla apagada | R03.21 | Plan d, retraso < 1 min | 1 |
| T03.22 | Play Store abre, muestra la cuenta y descarga/instala una app | R03.20 | App en User 0 | 1 |
| T03.23 | Android 17: guía de extracción del APEX y prueba de instalación | R03.7 | Plan b2 en Android 17 | 1 |
| T03.24 | Repetir en Android 17 el login y el push | R03.7, R03.18, R03.21 | Plan c y d en Android 17 | 1 |
| T03.25 | Regresión OPPO: ruta de clonado (b1) y coexistencia | R03.14, R03.22 | Play Store real intacta; sin cuentas cruzadas | 1 |
| T03.26 | Fijar versión de GMS y bloquear su actualización desde Play Store del box | R03.5 | Versión estable tras 24 h | 0,5 |
| T03.27 | Reactivar el tratamiento de excepciones de GMS: registrar y reiniciar, no tragar | R03.17 | Crash simulado reinicia el proceso | 0,5 |

Suma fase B: 16,5 días de trabajo directo; con estabilización y arreglos que
aparezcan (la parte incierta, estimación del veredicto de 20-40 días en total) la
estimación realista es **15-25 días adicionales tras el spike**.

## Resumen

| Bloque | Días |
|---|---|
| Spike | 10 |
| Fase B (trabajo directo) | ≈ 17 |
| Fase B con imprevistos | 25-35 |
| Si se pivota a microG | 8-12 |
