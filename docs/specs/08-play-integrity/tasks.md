# Spec 08: Play Integrity. Tareas

Estado: Borrador
Fecha: 2026-10-29
Requisitos: [requirements.md](requirements.md). Diseño: [design.md](design.md).
Todas las mediciones requieren la spec 03 (al menos el spike con GMS operativo).

| ID | Tarea | Req. | Verificación | Días |
|---|---|---|---|---|
| T08.1 | Crear plantilla `resultados.md` (columnas de R08.3) y fijar la app comprobadora con el propietario | R08.1, R08.3 | Plantilla y nombre de app registrados | 0,5 |
| T08.2 | Medir E1 fuera del box en el OPPO (3 repeticiones) | R08.2 | Fila rellenada | 0,5 |
| T08.3 | Medir E2 dentro del box en OPPO (GMS virtual, hito M3) | R08.4, R08.5 | Fila rellenada; logs si falla | 0,5 |
| T08.4 | Medir E2 en Android 16 (ROM china) en el hito M3 | R08.4, R08.5 | Fila rellenada | 0,5 |
| T08.5 | Medir E2 en Android 17 | R08.4 | Fila rellenada | 0,5 |
| T08.6 | Medir E5 (con/sin Play Store virtual) y E4 (estado de DroidGuard) | R08.6 | Dos filas | 1 |
| T08.7 | Implementar interruptor de depuración de propiedades coherentes en `VirtualSpoof.cpp` y medir E3 | R08.6, R08.7, R08.8 | E3 medido; pasos b-d en verde | 1 |
| T08.8 | Análisis: informe de factores y recomendación (mantener o cambiar el spoof) | R08.6, R08.7, R08.11 | Informe con matriz completa | 0,5 |
| T08.9 | Repetir medición en M4 y en cada candidata de versión | R08.4 | Nuevas filas fechadas | 0,5 por pasada |
| T08.10 | Texto de aviso sobre banca y apps de pago | R08.10 | Texto en documentación del producto | 0,5 |

Estimación: 5-6 días en total (la mayoría esperando a spec 03), más 0,5 por pasada de
release.

## Criterios de cierre

- Matriz E1-E5 con al menos una medición por celda aplicable.
- Veredicto registrado en M3, M4 y la candidata de versión, en Android 16 y 17.
- Recomendación sobre las propiedades coherentes; sin tareas de evasión (R08.9).
