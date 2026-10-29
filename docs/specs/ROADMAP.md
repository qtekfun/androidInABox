# Roadmap de la fase 1

Base: veredicto de la fase 0 ([veredicto.md](../fase0/veredicto.md)) y decisiones
ADR-0004 (GMS real) y ADR-0005 (Android Auto y Wear OS).

## Dispositivos de prueba

| Dispositivo | Uso |
|---|---|
| Móvil con Android 16 (ROM china, sin GMS) | Objetivo principal |
| Móvil con Android 17 (ROM china, sin GMS) | Objetivo principal |
| OPPO CPH2841, Android 16, ROM EEA con GMS | Referencia y comparación |
| Huawei Watch GT Runner 2 (vía Huawei Health) | Spec 02 |
| Reloj Wear OS (pendiente de llegar) | Spec 06 |
| Coche o head unit con Android Auto | Spec 07 (por confirmar) |

## Dependencias

```mermaid
graph LR
  S05[05 mantenimiento] --> S01[01 split APK]
  S01 --> S03[03 GMS real]
  S02[02 notificaciones] --> S04[04 segundo plano]
  S03 --> S04
  S03 --> S06[06 Wear OS]
  S03 --> S07[07 Android Auto]
  S02 --> S06
  S02 --> S07
  S03 --> S08[08 Play Integrity]
```

## Hitos

| Hito | Contenido | Criterio de salida |
|---|---|---|
| **M1. Motor limpio** | 05 (parte bloqueante) y 01 | Gmail y Play Store clonados de un sistema con GMS abren dentro del box en Android 16 y 17. CI verde |
| **M2. Notificaciones** | 02 | NotifProbe pasa e y f: nombre de app y perfil, icono, tags, respuesta por broadcast y por servicio. Llega al Huawei GT Runner 2 |
| **M3. Spike de GMS real** | 03, tarea de spike | Decisión documentada: GMS real viable o no en Android 16 y 17 de ROM china. Si no, ADR para volver a microG |
| **M4. Cadena crítica** | 03 completo y 04 | Pasos a–f del plan de pruebas en verde en Android 16 y 17 con WhatsApp y Gmail. Mensajes en menos de 1 minuto a los 60 minutos de inactividad |
| **M5. Wear OS** | 06 | Reloj emparejado con la app compañera del box y notificaciones de WhatsApp del box en el reloj; o conclusión documentada de inviabilidad |
| **M6. Android Auto** | 07 (spike) | Conclusión documentada con evidencias; si es viable, spec de implementación |
| **Transversal** | 08 | Nivel de Play Integrity medido en cada hito a partir de M3 |

M2 no depende de GMS y puede avanzar en paralelo a M1 y M3.

## Reglas de ejecución

- Cada tarea termina con un commit pequeño, su verificación descrita en el mensaje y la
  CI en verde.
- Las pruebas en dispositivo las ejecuta el propietario. Las tareas indican qué lanzar
  y qué evidencia devolver (`scripts/fase0/diag.sh`).
- Los APKs de Google nunca entran en ningún repo. Ver
  [fuentes-gms.md](../investigacion/fuentes-gms.md).
