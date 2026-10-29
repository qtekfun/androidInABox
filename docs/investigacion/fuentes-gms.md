# De dónde sacar el GMS real para instalarlo dentro del box

Fecha: 2026-10-29. Contexto: ADR-0004 (GMS real como ruta principal).

Reglas:
- El repositorio **no contiene ni contendrá** APKs de Google.
- La app **no los descargará** por el usuario. El usuario los obtiene y los instala él.
- Antes de instalar, verificar siempre la firma (ver el final).

## Hallazgo principal: el GMS de una ROM es un APK único

Las actualizaciones de Play llegan como split APK, que el motor no soporta. El GMS
**preinstalado en una ROM** es un APK monolítico.

Medido en el OPPO CPH2841 (Android 16, ROM EEA):

| Paquete | Ruta de fábrica | Tamaño | Versión de fábrica | Versión actualizada |
|---|---|---|---|---|
| `com.google.android.gms` | `/product/priv-app/GmsCore/GmsCore.apk` (+ `m/independent/AndroidPlatformServices.apk`) | 224 MB | 26.18.33 | 26.43.30 (13 APK en `/data/app`) |
| `com.android.vending` | `/product/priv-app/Phonesky/Phonesky.apk` | 100 MB | 51.5.21 | 53.7.23 (8 APK) |
| `com.google.android.gsf` | `/my_product/non_overlay/priv-app/GoogleServicesFramework/GoogleServicesFramework.apk` | 4 MB | 16-15304122 | — |

Por tanto, mientras no exista soporte de splits, **la fuente buena es una ROM o un
paquete de GApps**, no APKMirror ni Play.

## Fuentes, en orden de preferencia

### 1. Partición de fábrica de un móvil con GMS que tengas
- Es la fuente más rápida y la versión cuadra con la de Android de ese móvil.
- Solo lectura; no necesita root:

  ```bash
  mkdir -p ~/gms-local/a16 && cd ~/gms-local/a16
  adb pull /product/priv-app/GmsCore/GmsCore.apk
  adb pull /product/priv-app/Phonesky/Phonesky.apk
  adb shell pm path com.google.android.gsf   # la ruta varía según el fabricante
  adb pull <ruta de GoogleServicesFramework.apk>
  ```
- Limitación: solo sirve para la versión de Android de ese móvil.

### 2. MindTheGapps (paquetes GApps de LineageOS)
- Es el enlace oficial de la wiki de LineageOS. Las releases están firmadas y llevan
  `sha256sum`.
- Android 16 (arm64): https://github.com/MindTheGapps/16.0.0-arm64/releases. Última
  vista: `20261023`, 499 MB.
  - Contiene `system/product/priv-app/GmsCore/GmsCore.apk` (187 MB),
    `Phonesky/Phonesky.apk` y `system/system_ext/priv-app/GoogleServicesFramework/GoogleServicesFramework.apk`,
    todos como APK único.
  - También `AndroidAutoStub.apk`, un stub: el Android Auto real se baja de Play.
- Android 17 (arm64): https://github.com/MindTheGapps/17.0.0-arm64/releases. Última
  vista: `20261022`, 519 MB.
  - **Cambio importante:** ya no hay `GmsCore.apk`. GMS viene dentro de un APEX,
    `system/product/apex/com.google.android.gmssystem.prodvic.apex` (139 MB).
  - Phonesky (102 MB), GSF y `AndroidAutoStub` siguen siendo APK.
  - Para Android 17 hay que extraer el APK del APEX. Un APEX es un zip que contiene
    `apex_payload.img` (ext4 o erofs). Herramientas: `deapexer` de AOSP, o
    `fsck.erofs --extract` / `debugfs`.
- Útil además para el motor: trae los XML que declaran qué permisos privilegiados
  espera GMS. El box tendrá que emularlos.
  - `etc/permissions/privapp-permissions-google-product.xml`
  - `etc/default-permissions/default-permissions-google.xml`
  - `etc/sysconfig/google.xml`
  - `etc/sysconfig/google-hiddenapi-package-allowlist.xml`

Listar un zip sin descargarlo entero (solo lee el índice por HTTP Range):

```bash
python3 -I scripts/investigacion/remotezip.py <URL del .zip> | grep -E "\.apk|\.apex"
```

### 3. Imágenes de fábrica u OTA de Pixel (fuente oficial de Google)
- https://developers.google.com/android/images, con builds para Android 16 y 17.
- Proceso:
  1. Descomprimir la imagen.
  2. Sacar `product.img` (o extraerlo del `payload.bin` de la OTA con
     `payload-dumper-go`).
  3. Extraer `/priv-app/GmsCore`, `Phonesky` y GSF con `fsck.erofs --extract=...`
     (paquete `erofs-utils`).
- Más pesada (2–3 GB por imagen), pero es la referencia "limpia". En Android 17
  probablemente también vendrá como APEX (**sin verificar**).

### 4. NikGapps (alternativa)
- https://nikgapps.com/downloads y SourceForge (`Releases/Android-16`). Paquetes no
  oficiales con varios tamaños.
- Usar solo si fallan las anteriores.

### No usar por ahora
- **APKMirror y la Play Store:** GMS y Play Store se distribuyen como bundle de
  splits (`.apkm`). Sirven cuando el motor soporte splits (fase 1).

## Verificar la firma antes de instalar

```bash
apksigner verify --print-certs GmsCore.apk | grep -E "SHA-1|SHA-256"
```

- GMS, GSF y Play Store de Google se firman con el certificado de plataforma de Google
  cuyo SHA-1 es `38:91:8A:45:3D:07:19:93:54:F8:B1:9A:F0:5E:C6:56:2C:ED:57:88`.
  Comparar con el GMS instalado en un móvil de confianza:

  ```bash
  adb shell dumpsys package com.google.android.gms | grep -A2 signatures
  ```
- Si no coincide, **no instalarlo**.

## Relación con los dispositivos de prueba

| Dispositivo | Fuente de GMS recomendada |
|---|---|
| Móvil Android 16 (ROM china) | MindTheGapps 16, o pull de un móvil con GMS y Android 16 (el OPPO) |
| Móvil Android 17 (ROM china) | MindTheGapps 17 (extraer de APEX), o imagen Pixel de Android 17 |
