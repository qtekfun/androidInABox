# Aviso "Riesgos detectados" al instalar androidInABox

## Qué es

Algunas capas de fabricante analizan cada APK al instalarlo y pueden mostrar un aviso
como este:

> **Riesgos detectados con "androidInABox"**
> Esta aplicación puede ocasionar riesgos graves, como infracciones de privacidad…
> [Ignorar] [Corregir ahora]

Lo hemos visto con ColorOS (OPPO/OnePlus/realme), cuyo antivirus es
`com.coloros.phonemanager`. Es posible también con MIUI/HyperOS, MagicOS y las
versiones chinas de otras capas (sin verificar).

## Por qué aparece

androidInABox es un **espacio virtual**: ejecuta otras apps dentro de sí misma. Para
eso:

- carga y ejecuta el código de otras apps;
- intercepta llamadas a servicios del sistema;
- solicita muchos permisos para poder concedérselos a las apps que contiene.

Los antivirus clasifican así, de forma genérica, a todos los motores de virtualización
(VirtualApp, BlackBox y derivados, y también apps conocidas de clonado), porque el
malware a veces usa las mismas técnicas. **El aviso no indica que se haya encontrado
nada malicioso.** Es una regla heurística sobre el tipo de app.

## Qué hacer

- Pulsa **Ignorar**.
- **No pulses "Corregir ahora":** desinstala la app y **borra todo el contenido del
  espacio virtual** (apps, cuentas y datos de los perfiles).
- El aviso puede repetirse tras cada actualización. Si la capa lo permite, añade la app
  a la lista de confianza del gestor del teléfono.

## Qué hacemos para reducirlo

- **Sin telemetría:** ningún dato sale del dispositivo (ADR-0003).
- **Builds firmadas** con la clave de release del proyecto. SHA-256 del certificado en el
  [README](../../README.md); compruébalo antes de instalar.
- **Manifiesto más limpio:** se eliminan los permisos protegidos que Android nunca
  concede a una app normal y que solo suben la puntuación de riesgo (en curso).
- **Posibles mejoras futuras** (no garantizadas):
  - renombrar los paquetes Java heredados del upstream, que muchos antivirus reconocen
    por nombre;
  - pedir al fabricante que revise el falso positivo.
- **Código abierto:** cualquiera puede auditar qué hace la app.
