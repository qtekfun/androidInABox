# androidInABox

Espacio virtual para Android sin root: apps clonadas con cuentas independientes,
servicios de Google (GMS o microG, aportados por el usuario) dentro del espacio y
notificaciones publicadas en el sistema real.

Estado: **fase 0**, prueba de viabilidad del motor (fork de
[NewBlackbox](https://github.com/ALEX5402/NewBlackbox), Apache 2.0).

## Código y builds

- Motor: submódulo `engine/` → [qtekfun/androidInABox-engine](https://github.com/qtekfun/androidInABox-engine) (fork de NewBlackbox, rama `aiab-main`).
  Clonar con `git clone --recurse-submodules`.
- Builds de prueba firmadas: [Releases](https://github.com/qtekfun/androidInABox/releases). Cada push a `main` publica `dev-<n>`.
  SHA-256 del certificado: `84:EC:8D:B0:8D:E8:49:7D:49:61:6B:A5:D2:D1:9B:A4:D2:67:97:04:EE:46:80:4E:5F:8C:8F:E9:F3:FB:9D:73`.

## Documentación

- Veredicto: [docs/fase0/veredicto.md](docs/fase0/veredicto.md)
- Cómo funciona el motor: [docs/fase0/arquitectura-motor.md](docs/fase0/arquitectura-motor.md)
- Riesgos por versión de Android: [docs/fase0/riesgos-android.md](docs/fase0/riesgos-android.md)
- Resultados en dispositivo: [docs/fase0/resultados-dispositivo.md](docs/fase0/resultados-dispositivo.md)
- Plan de pruebas: [docs/fase0/plan-pruebas.md](docs/fase0/plan-pruebas.md)
- Compilación: [docs/fase0/compilacion.md](docs/fase0/compilacion.md)
- Decisiones: [docs/adr/](docs/adr/)

Este repositorio no incluye ni incluirá APKs de Google.
