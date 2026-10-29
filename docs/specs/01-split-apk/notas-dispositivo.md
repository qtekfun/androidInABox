# Spec 01: resultados en dispositivo

## OPPO CPH2841, Android 16, 2026-10-29

**WhatsApp clonado del sistema real.** En el sistema son 5 APK: `base.apk`,
`split_config.arm64_v8a.apk`, `split_config.xxxhdpi.apk`, `split_i18n_ca.apk` y
`split_i18n_es.apk`. Las librerías nativas solo están en el split de ABI; el base no
tiene ninguna.

- Arranca dentro del box hasta la pantalla de bienvenida, sin `ClassNotFound`,
  `UnsatisfiedLink` ni `Resources$NotFound`.
- El proceso del invitado (UID del host) tiene mapeados los 5 APK, incluido
  `split_config.arm64_v8a.apk`. Lee las librerías desde el split, así que el
  directorio `lib/` del clon en el box queda vacío, y es correcto.
- Sus datos están en el directorio virtual
  (`.../blackbox/data/user/0/com.whatsapp/databases/...`). Es independiente del
  WhatsApp real, que corre a la vez con su propio UID.
- Los clones no escriben `splits.conf`: leen `splitSourceDirs` del `ApplicationInfo`
  del sistema en cada arranque (T01.9).

**Pendiente:**
- Instalación desde `.apkm`/`.apks` y desde varios APK: necesita la API del host y la
  UI (T01.11, T01.12).
- Las apps de sistema actualizadas (Gmail en el OPPO) no aparecen en la lista para
  clonar, porque la UI heredada filtra `FLAG_SYSTEM` (spec 09).
