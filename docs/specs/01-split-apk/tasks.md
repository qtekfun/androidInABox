# Spec 01: split APK y bundles — tareas

Estado: Borrador

Orden de ejecución. Cada tarea ≤ 1 día y termina con un commit pequeño y la CI en verde.
Rutas relativas a `engine/`; `B/` = `Bcore/src/main/java/top/niunaijun/blackbox/`.
Dependencia previa del roadmap: la parte bloqueante de la spec 05 (T05.1 a T05.4).

| ID | Tarea | Cubre | Verificación | Días |
|---|---|---|---|---|
| T01.1 | Diagnóstico del clon de sistema: añadir logs temporales de `splitSourceDirs` en `BlackBoxCore.installPackageAsUser`, `Settings.updatePackageLP`, `PackageManagerCompat.generateApplicationInfo` y `BActivityThread.handleBindApplication`; reproducir el fallo de Play Store y anotar en qué paso se pierden. Quitar los logs al cerrar. | R01.10, RK1 | Informe corto en el mensaje del commit y en design.md §1 (sustituir "sin verificar") | 0.5 |
| T01.2 | `SplitManifestReader` y `SplitSet` (validaciones R01.5). Fixtures de APK mínimos en `Bcore/src/test/resources` generados por script | R01.4, R01.5 | `SplitManifestReaderTest`, `SplitSetTest` | 1 |
| T01.3 | `BundleDetector` y `SafeUnzip` (límites 512 entradas / 4 GiB, anti zip-slip, solo `*.apk`) | R01.3, R01.8 | `BundleDetectorTest`, `SafeUnzipTest` | 1 |
| T01.4 | `SplitSelector` (ABI, densidad, idioma) | R01.6 | `SplitSelectorTest` con tabla de casos | 1 |
| T01.5 | `BEnvironment.getSplitsConf`, campos `transient` en `BPackage`, lectura/escritura de `splits.conf`, carga en `Settings.updatePackageLP` | R01.16, R01.17 | `SplitPersistTest`; instalar con build vieja y actualizar encima | 1 |
| T01.6 | `InstallOption.FLAG_SPLIT_SET` y rama de `installPackageAsUserLocked` para directorio de staging (detección, extracción, selección, espacio libre, limpieza) | R01.1, R01.2, R01.5, R01.7, R01.9 | `SplitInstallTest.bundle*`, `.multiApk`, `.invalid*`, `.noSpace` | 1 |
| T01.7 | `CopyExecutor`, `NativeUtils.copyNativeLib` y `AbiUtils` sobre base + config splits | R01.15, R01.18, R01.19 | `AbiUtilsTest.configSplit`; instalar bundle con libs en `config.arm64_v8a` y comprobar `lib/` | 1 |
| T01.8 | `PackageManagerCompat.generateApplicationInfo`, `generatePackageInfo`, `getResources`, y `BPackageManager` (l.681/830) | R01.12, R01.14 | `GenerateApplicationInfoTest`; app de prueba vuelca los campos | 1 |
| T01.9 | Clon del host: conservar `splitSourceDirs`, y comparar splits en `Settings.updatePackageLP`/`reInstallBySystem` | R01.10, R01.11 | Clonar Gmail y actualizarlo en el host; log `reInstallBySystem` | 1 |
| T01.10 | Classloader y recursos en `BActivityThread` (incluye `getClassLoader` alternativo); comprobar `LoadedApk` en Android 16 | R01.13, R01.14 | App de prueba carga una clase y un recurso que solo están en splits | 1 |
| T01.11 | API del host: `BlackBoxCore.installPackageAsUser(List<Uri>, userId)` con staging; sin cambiar la firma AIDL | R01.1, R01.2 | Test instrumentado con dos `Uri` de fixtures | 1 |
| T01.12 | UI: selector múltiple, bundles, mensajes de error y de "falta el base" (`ListActivity.kt`, `AppsRepository.kt`, strings) | R01.20, R01.21 | Paso manual con `.apkm` y con 3 `.apk` | 1 |
| T01.13 | Comprobación de firma de splits (evaluar `ApkSignatureVerifier`; si no, aviso en log) | R01.22 | `SplitInstallTest.badSignature` o aviso verificado en logcat | 0.5 |
| T01.14 | Validación en dispositivo (propietario): plan de §8 de design.md en Android 16 y 17; recoger `diag.sh` | R01.10 a R01.19 | Evidencias adjuntas; marcar M1 (splits) | 1 |
| T01.15 | Cierre: actualizar estados de la spec a "Implementado", registrar APEX como fuera de alcance en `docs/investigacion/fuentes-gms.md` si procede | todos | Revisión del propietario | 0.5 |

**Total estimado: 13,5 días** (más T01.1 como puerta: si revela otra causa, replanificar T01.5 a T01.10).

## Notas

- T01.2 a T01.4 son independientes entre sí y se pueden hacer en cualquier orden; T01.6 las necesita.
- T01.11 y T01.12 pueden ir en paralelo con T01.8 a T01.10 si hay dos personas; con una, mantener el orden.
- Los tests instrumentados necesitan un dispositivo o emulador con Android 13 o superior; las pruebas con
  GMS real quedan para la spec 03.
