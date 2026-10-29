# ADR-0006: Estructura de repos, CI de releases y firma de APKs

- Estado: aceptada (decisiones del propietario: repo público, opción "fork + submódulo")
- Fecha: 2026-10-29

## Decisión

- **Repos:**
  - `qtekfun/androidInABox`: documentación, herramientas y CI.
  - `qtekfun/androidInABox-engine`: fork público de NewBlackbox, rama `aiab-main`.
  - El motor entra en el repo principal como submódulo en `engine/`.
- **Paquete:** `com.qtekfun.androidinabox`. El upstream es `top.niunaijun.blackbox`;
  ambos pueden convivir instalados.
- **CI** (`.github/workflows/release.yml`):
  - Un push a `main` publica una pre-release `dev-<n>`.
  - Un tag `v*` publica una release.
  - El `versionCode` es `1000 + número de ejecución`, así que cada build se instala
    encima de la anterior.
  - Los tags de release no se borran ni se reutilizan.
- **Firma:**
  - Clave RSA 4096 en PKCS12, alias `aiab`, válida hasta 2054.
  - SHA-256 del certificado:
    `84:EC:8D:B0:8D:E8:49:7D:49:61:6B:A5:D2:D1:9B:A4:D2:67:97:04:EE:46:80:4E:5F:8C:8F:E9:F3:FB:9D:73`
  - El keystore y las contraseñas viven fuera del repo, en local y en los secretos de
    GitHub Actions: `AIAB_KEYSTORE_B64`, `AIAB_KEYSTORE_PASSWORD`, `AIAB_KEY_ALIAS`,
    `AIAB_KEY_PASSWORD`.
- **Comprobaciones que hacen fallar la CI:**
  - firma con un certificado distinto del esperado;
  - `.so` no alineada a 16 KB;
  - reaparición de la telemetría del upstream (ADR-0003).

## Consecuencias

- **Perder la clave impide actualizar.** Habría que desinstalar, y eso borra todo el
  contenido del box. El propietario debe guardar una copia de seguridad fuera del
  portátil.
- El historial del fork conserva los autores del upstream, con sus correos. Es
  inherente a un fork; los commits propios usan solo la identidad del propietario.
