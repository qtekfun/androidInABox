# Specs (spec-driven development)

Cada funcionalidad se define antes de programarla, en una carpeta
`docs/specs/NN-nombre/` con tres ficheros que se escriben y aprueban en orden.

| Fichero | Contenido | Quién lo aprueba |
|---|---|---|
| `requirements.md` | Qué y por qué: historias de usuario y criterios de aceptación en formato EARS | Propietario |
| `design.md` | Cómo: componentes del motor afectados, flujo, decisiones y alternativas, riesgos, plan de verificación | Propietario |
| `tasks.md` | Pasos pequeños e implementables, cada uno con su verificación y los requisitos que cubre | Se ejecuta |

No se empieza `design.md` sin `requirements.md` aprobado, ni se programa sin
`tasks.md`. Si al implementar algo contradice la spec, primero se corrige la spec.

## Convenciones

- **IDs:**
  - Requisitos: `R<spec>.<n>` (p. ej. `R02.3`).
  - Tareas: `T<spec>.<n>`.
  - Cada tarea indica qué requisitos cubre; cada requisito, cómo se verifica.
- **EARS** (criterios de aceptación). Las palabras clave van en inglés para que el
  patrón se reconozca:
  - `WHEN <evento> THE <sistema> SHALL <respuesta>`
  - `WHILE <estado> THE <sistema> SHALL <respuesta>`
  - `IF <condición no deseada> THEN THE <sistema> SHALL <respuesta>`
  - `WHERE <característica opcional> THE <sistema> SHALL <respuesta>`
  - `THE <sistema> SHALL <respuesta>` (siempre)
- **Sistemas** que se nombran en los criterios:
  - *el motor*: Bcore;
  - *el host*: la app `com.qtekfun.androidinabox` en el sistema real;
  - *el box*: el espacio virtual;
  - *la app invitada*: la app que corre dentro del box.
- **Verificación:** cada requisito apunta a una prueba concreta, de uno de estos tipos:
  - un paso del [plan de pruebas](../fase0/plan-pruebas.md);
  - una comprobación con NotifProbe (`tools/notifprobe`) u otra app de prueba;
  - un test unitario o instrumentado;
  - una comprobación de la CI.
- **Spikes:** cuando la viabilidad es dudosa (GMS real, Android Auto, Wear OS), la spec
  empieza con una tarea de investigación acotada en días, con criterio de salida
  *continuar / pivotar / abandonar*. Si sale "abandonar", la spec se cierra con la
  conclusión documentada.
- **Estado** en la cabecera de cada fichero:
  `Borrador → En revisión → Aprobado → Implementado`.
- **Decisiones** que cambien la arquitectura o el alcance: ADR en `docs/adr/`.

## Índice

Ver [ROADMAP.md](ROADMAP.md) para el orden, las dependencias y los hitos.

| Spec | Tema | Tipo |
|---|---|---|
| [01-split-apk](01-split-apk/) | Instalar y clonar apps en split APK y bundles | Funcionalidad |
| [02-notificaciones](02-notificaciones/) | Presentación, acciones y respuesta de notificaciones; reloj Huawei | Funcionalidad |
| [03-gms-real](03-gms-real/) | GMS, GSF y Play Store reales dentro del box | Spike + funcionalidad |
| [04-segundo-plano](04-segundo-plano/) | Supervivencia de procesos y push (FCM) | Funcionalidad |
| [05-mantenimiento-motor](05-mantenimiento-motor/) | APIs ocultas, `ClientTransaction` en Android 17, JniHook, limpieza | Mantenimiento |
| [06-wear-os](06-wear-os/) | Relojes Wear OS emparejados con apps del box | Spike + funcionalidad |
| [07-android-auto](07-android-auto/) | Android Auto con apps del box | Spike |
| [08-play-integrity](08-play-integrity/) | Medir y mejorar el nivel de Play Integrity | Medición |
| 09-ui (pendiente) | Rediseño completo de la interfaz del host | Funcionalidad |
