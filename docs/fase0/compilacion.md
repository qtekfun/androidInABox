# Fase 0: compilar NewBlackbox

Upstream evaluado: https://github.com/ALEX5402/NewBlackbox, commit `89b5983`
(19-07-2026), sin modificaciones.

## Versiones que usa el proyecto

| Pieza | Versión | Dónde se declara |
|---|---|---|
| Gradle wrapper | 8.14.5 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.13.2 | `gradle/libs.versions.toml` |
| Kotlin (módulo `app`) | 1.9.23 | `gradle/libs.versions.toml` |
| compileSdk / targetSdk / minSdk | 35 / **28** / 21 | `build.gradle` (raíz) |
| NDK | 29.0.13846066 (ndk-build, `Android.mk`) | `Bcore/build.gradle`, `app/build.gradle` |
| Java source/target | 21 | `Bcore/build.gradle`, `app/build.gradle` |
| ABIs | arm64-v8a, armeabi-v7a (sin x86) | `Bcore/build.gradle`, `Application.mk` |

## JDK necesario: 21

- El README del upstream pide JDK 17. **No basta**: los módulos compilan con
  `sourceCompatibility 21`.
- **JDK 25 falla** al configurar el proyecto. El plugin de Kotlin 1.9.23 no entiende la
  cadena de versión:

  ```
  A problem occurred configuring project ':black-reflection'.
  > 25.0.4.1
  ```
- Con **Temurin 21.0.13** compila sin tocar nada.

## Paquetes del SDK

```bash
sdkmanager --install "ndk;29.0.13846066" "platforms;android-35" "build-tools;35.0.0"
```

## Comandos

```bash
git clone https://github.com/ALEX5402/NewBlackbox.git
cd NewBlackbox
git checkout 89b5983
export JAVA_HOME=/ruta/a/jdk-21
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew --no-daemon assembleDebug
```

## Resultado

- `BUILD SUCCESSFUL in 5m 34s`, 82 tareas, en un portátil de 3 núcleos y 15 GB de RAM.
  Solo hay avisos de Kotlin y de lint; ningún error.
- Aviso de recursos sin impacto: `values-zh-rCN/strings.xml: Multiple substitutions
  specified in non-positional format of string resource string/set_location`.
- Salida en `app/build/outputs/apk/debug/`, unos 9 MB cada uno:
  `BlackBox_4.0.0_arm64-v8a-debug.apk`, `..._armeabi-v7a-debug.apk` y
  `..._universal-debug.apk`.
- Paquete: `top.niunaijun.blackbox`, versionName 4.0.0, `targetSdkVersion 28`.

## Páginas de 16 KB

| Comprobación | Resultado |
|---|---|
| Segmentos LOAD de `lib/*/libblackbox.so` (`llvm-readelf -lW`) | alineación `0x4000` (16 KB) |
| `zipalign -c -P 16 -v 4` del APK universal | `Verification succesful` |

Flags que lo garantizan:
- `Android.mk`: `-Wl,-z,max-page-size=16384`
- `Application.mk`: `APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true`

Las `.so` van comprimidas (`useLegacyPackaging true`) y se extraen al instalar.

Dobby se incluye como `libdobby.a` precompilado, de versión desconocida, y se enlaza
estáticamente. Consulta el tamaño de página en tiempo de ejecución (`sysconf`), pero no
se ha probado en un dispositivo con páginas de 16 KB.

## Herramienta auxiliar de la fase 0

`tools/notifprobe/` contiene una app mínima, compilable sin Gradle, que publica
notificaciones con tags, icono de recurso, acción de abrir y respuestas RemoteInput por
broadcast y por servicio. Se usa para medir los pasos e y f del plan de pruebas sin
depender de WhatsApp. Ver su `README.md`.
