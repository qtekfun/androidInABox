# Spec 01: split APK y bundles — diseño

Estado: Borrador

Rutas relativas a `engine/`. Se abrevia `B/` = `Bcore/src/main/java/top/niunaijun/blackbox/`.
Lo no comprobado en el código o en un dispositivo se marca "(sin verificar)".

## 1. Situación actual (verificada en el código)

- `B/core/system/pm/BPackageManagerService.installPackageAsUserLocked(String file, InstallOption, int userId)`
  trata `file` como un único APK: lo copia a `cache/<uuid>.apk` si es `Uri` (`FLAG_URI_FILE`),
  llama a `getPackageArchiveInfo`, `AbiUtils.isSupport(apkFile)` y `parserApk(file)`
  (`PackageParserCompat.createParser/parsePackage/collectCertificates`).
- `B/core/system/pm/installer/CopyExecutor.exec` copia solo `ps.pkg.baseCodePath` a
  `BEnvironment.getBaseApkDir(pkg)` (`data/app/<pkg>/base.apk`) y llama a
  `NativeUtils.copyNativeLib(baseCodePath, appLibDir)`, que abre un único zip y busca `lib/<Build.CPU_ABI>/`.
- `B/core/system/pm/PackageManagerCompat.generateApplicationInfo` hace `new ApplicationInfo(p.applicationInfo)`
  y fija `sourceDir/publicSourceDir = p.baseCodePath`, `nativeLibraryDir` (si no es `FLAG_SYSTEM`) y
  `sharedLibraryFiles`. `PackageManagerCompat.getResources` añade solo `baseCodePath` al `AssetManager`.
- Persistencia: `BPackageSettings.save()` serializa `BPackage` + `InstallOption` + estado por usuario en
  `BEnvironment.getPackageConf(pkg)`. `BPackage` guarda `baseCodePath` y `applicationInfo` (parcelable).
- Carga: `B/core/system/pm/Settings.updatePackageLP` lee ese fichero; **si el parcel no se puede leer, captura
  la excepción y borra el directorio del paquete** (`FileUtils.deleteDir(app)`). Es la razón de la decisión D2.
- Clones del host (`FLAG_SYSTEM`): `BlackBoxCore.installPackageAsUser(String pkg, int userId)` pasa solo
  `packageInfo.applicationInfo.sourceDir`; luego `aPackage.applicationInfo` se sustituye por el del host
  (`getPackageInfo(pkg, 0).applicationInfo`), que **sí trae** `splitSourceDirs` del sistema real.
  `Settings.updatePackageLP`/`reInstallBySystem` comparan solo `sourceDir`.
- Hallazgo sin explicar: en teoría el clon de sistema conserva `splitSourceDirs` por la copia de
  `ApplicationInfo`, pero el fallo medido (`DexPathList[[zip file ".../base.apk"`) indica que algún paso lo pierde
  (sin verificar cuál: copia en `BPackage(PackageParser.Package)`, serialización por binder, o el `LoadedApk` de
  `BActivityThread.handleBindApplication`). La tarea T01.1 lo aclara antes de programar.
- `B/app/BActivityThread.handleBindApplication`: `createPackageContext(applicationInfo)` (contexto del host con
  `CONTEXT_INCLUDE_CODE | CONTEXT_IGNORE_SECURITY`), `BRLoadedApk._set_mApplicationInfo(applicationInfo)`.
  El classloader lo construye `LoadedApk` a partir de `mApplicationInfo` (base + `splitSourceDirs`, y
  `splitNames`/`splitDependencies` si el manifiesto pide `isolatedSplits`) (sin verificar el momento exacto).
  `BActivityThread.getClassLoader(ApplicationInfo)` (~l.634) es un camino alternativo que solo usa `sourceDir`.

## 2. Decisiones

### D1. Leer los nombres de split del manifiesto binario, no del bundle ni del `PackageParser` oculto
Clase nueva `B/core/system/pm/split/SplitManifestReader`: abre el APK con un `AssetManager`
(`BRAssetManager` ya existe; se usa `addAssetPath`) y lee `AndroidManifest.xml` con
`openXmlResourceParser` para extraer `package`, `split`, `configForSplit`, `isFeatureSplit`,
`isSplitRequired`, `versionCode`, `isolatedSplits`. Motivos: funciona igual con `.apks`, `.apkm` y `.xapk`;
no depende de `PackageParser.parseApkLite` (oculto, firma cambiante, sin verificar en Android 17);
`getPackageArchiveInfo` falla con un split suelto.

### D2. Persistir los splits en un fichero aparte, no en el parcel de `BPackage`
Fichero nuevo `data/app/<pkg>/splits.conf` (texto, una línea por split en orden: `nombre<TAB>fichero`),
con helper `BEnvironment.getSplitsConf(pkg)` (nuevo). `BPackage` gana dos campos **no parcelables**
(`transient`): `List<String> splitNames` y `List<String> splitCodePaths`, rellenados al instalar y en
`Settings.updatePackageLP`. Así los paquetes ya instalados siguen leyéndose (R01.17) y no se toca el formato
de `package.conf`. Si falta el fichero, el paquete se trata como sin splits.

### D3. Los splits de un clon del host se referencian, no se copian
Para `FLAG_SYSTEM` el motor sigue apuntando a `/data/app/...` del host (como hoy con el base). Se añade a la
comparación de `Settings.updatePackageLP` la de `splitSourceDirs` (R01.11). No se duplican 100–200 MB por clon.
Los datos y las libs nativas del clon siguen siendo las del sistema (`nativeLibraryDir` no se sobrescribe).

### D4. Selección de splits de configuración por clase
`SplitSelector` clasifica cada split con `configForSplit != null` por el sufijo del nombre:
- ABI: `config.arm64_v8a`, `config.armeabi_v7a`, `config.x86*`; se elige la que coincide con la ABI del box.
- Densidad: `config.ldpi|mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|tvdpi`; se elige la más cercana a
  `DisplayMetrics.densityDpi`.
- Idioma: `config.<código>`; se eligen los de `LocaleList` del sistema y `en`.
- Otros: se instalan.
El resto de splits (`isFeatureSplit`) se instalan siempre. Si no hay ninguna ABI compatible pero el base trae
libs, se acepta (apps con ABI en el base).

## 3. Componentes afectados

| Clase / fichero (relativo a `engine/`) | Cambio |
|---|---|
| `B/core/system/pm/split/BundleDetector` (nueva) | Decide APK / bundle / conjunto por contenido (R01.3) |
| `B/core/system/pm/split/SafeUnzip` (nueva) | Extracción con límites y anti zip-slip (R01.8) |
| `B/core/system/pm/split/SplitManifestReader` (nueva) | Atributos del manifiesto (D1) |
| `B/core/system/pm/split/SplitSelector` (nueva) | Filtro por ABI, densidad e idioma (R01.6) |
| `B/core/system/pm/split/SplitSet` (nueva) | Resultado: base, lista ordenada de splits, validaciones R01.5 |
| `B/entity/pm/InstallOption` | Nuevo flag `FLAG_SPLIT_SET = 1 << 4` (el `1 << 2` queda libre; no reutilizar) |
| `B/core/system/pm/BPackageManagerService.installPackageAsUserLocked` | Rama nueva para directorio de staging con varios APK; reutiliza `parserApk` sobre el base |
| `B/core/system/pm/BPackage` | Campos `transient` `splitNames`, `splitCodePaths` (D2) |
| `B/core/system/pm/installer/CopyExecutor` | Mueve splits a `data/app/<pkg>/split_<nombre>.apk`; libs de base y de config splits; escribe `splits.conf` |
| `B/utils/NativeUtils.copyNativeLib` | Acepta lista de APK; ABI según `Build.SUPPORTED_ABIS` (hoy `Build.CPU_ABI`, sin verificar equivalencia en Android 16) |
| `B/utils/AbiUtils` | Constructor sobre lista de APK (hoy un `File`; la caché `sAbiUtilsMap` por `File` se conserva) |
| `B/core/system/pm/PackageManagerCompat.generateApplicationInfo` | `splitSourceDirs`, `splitPublicSourceDirs`, `splitNames`; ver §4 |
| `B/core/system/pm/PackageManagerCompat.generatePackageInfo` | `pi.splitNames` (y `splitRevisionCodes`, sin verificar) |
| `B/core/system/pm/PackageManagerCompat.getResources` | `addAssetPath` de base y de cada split |
| `B/core/system/pm/Settings.updatePackageLP`, `reInstallBySystem` | Lee `splits.conf`; compara splits del host |
| `B/fake/frameworks/BPackageManager` (l.681 y 830) | Reemplazar `splitNames = {}` por los reales o `null` si no hay (rutas de respaldo) |
| `B/app/BActivityThread.getClassLoader` (~l.634) | Incluir `splitSourceDirs` en el `PathClassLoader` alternativo |
| `B/BlackBoxCore` | `installPackageAsUser(List<Uri>/File[], userId)` y staging (§5) |
| `app/src/main/java/top/niunaijun/blackboxa/view/list/ListActivity.kt` y `data/AppsRepository.kt` | Selector múltiple (`*/*`, `EXTRA_ALLOW_MULTIPLE`); mensajes |
| `B/core/system/pm/installer/CreatePackageExecutor`, `RemoveAppExecutor` | Sin cambios: ya borran `data/app/<pkg>` entero (R01.18/19) |

## 4. Flujo de datos

```
Selector (host, proceso principal)
  └─ BlackBoxCore.installPackageAsUser(uris, userId)
       copia cada Uri a cache/stage-<uuid>/in-N      (mismo UID que :black, sin permisos extra)
       └─ IBPackageManagerService.installPackageAsUser(ruta del directorio, FLAG_SPLIT_SET)
            [proceso :black] BPackageManagerService.installPackageAsUserLocked
              1. BundleDetector: por cada fichero -> APK suelto | bundle
              2. bundle -> SafeUnzip a stage/x/ (solo entradas *.apk; ignora toc.pb, info.json, obb)
              3. SplitManifestReader sobre cada APK -> SplitSet (validar R01.5)
              4. SplitSelector (R01.6) -> descarta splits no aplicables
              5. comprobar espacio (R01.7); AbiUtils sobre base + config.abi
              6. parserApk(base) -> PackageParser.Package (firma del base)
              7. Settings.getPackageLPw(...) ; BPackage.splitNames/splitCodePaths = rutas en stage
              8. BPackageInstallerService.installPackageAsUser(ps):
                   CreatePackageExecutor (borra y crea data/app/<pkg>)
                   CopyExecutor: mueve base -> base.apk, splits -> split_<n>.apk,
                                 libs de base + config.<abi> -> lib/, escribe splits.conf,
                                 actualiza rutas en BPackage
              9. save(); ComponentResolver; limpiar stage (R01.9)
Arranque de la app invitada (proceso :pN)
  BActivityThread.handleBindApplication
    -> BPackageManager.getPackageInfo -> generateApplicationInfo (con splits)
    -> createPackageContext + LoadedApk (classloader y Resources con base + splits)
```

Detalle de `generateApplicationInfo` (R01.12):
`ai.splitSourceDirs = ai.splitPublicSourceDirs = splitCodePaths.toArray()`,
`ai.splitNames = splitNames.toArray()` (arrays de igual longitud y orden). Para clones del host
(`FLAG_SYSTEM`) se conservan los arrays heredados de `p.applicationInfo`. Si el manifiesto del base declara
`isolatedSplits`, además habría que rellenar `ApplicationInfo.splitDependencies` (campo oculto,
sin verificar); se deja fuera de v1 y se registra un aviso (riesgo RK3).

## 5. Entrada de varios ficheros por el AIDL

`IBPackageManagerService.installPackageAsUser(String file, in InstallOption option, int userId)`
(`Bcore/src/main/aidl/.../IBPackageManagerService.aidl`) recibe un solo `String`. No se cambia la firma:
el host copia todo a un **directorio de staging** y pasa la ruta. El servidor distingue directorio de fichero
con `File.isDirectory()` y el flag. Ventajas: sin romper AIDL ni el APK del host ya instalado; el binder no
transporta rutas largas ni listas. El `Uri` único del flujo actual (`FLAG_URI_FILE`) sigue funcionando.

## 6. Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| Añadir campos al parcel de `BPackage`/`BPackageSettings` | `Settings.updatePackageLP` borra el paquete si no puede leerlo; exigiría migración (D2) |
| `PackageParser.parsePackage(dir)` en modo cluster para todo | Depende del `PackageParser` oculto, que exige coherencia estricta entre splits y se comporta distinto por versión (sin verificar en 17) |
| Nombres de split por convención del nombre de fichero | `.apks` de bundletool usa `splits/base-xxhdpi.apk`; no fiable |
| `PackageInstaller` real del sistema | Instalaría en el host con su UID real: contradice el box |
| Copiar los splits del host en cada clon | Duplica cientos de MB; ya se referencia el base (D3) |
| Soportar `.obb` de `.xapk` | Requiere escribir en `BEnvironment.getExternalObbDir` y permisos de almacenamiento; fuera de v1 (ver preguntas) |
| Instalar todos los config splits | Cientos de MB de más y ABI mezcladas (R01.6) |

## 7. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| RK1 | La causa real del fallo del clon de sistema no es la que se supone | T01.1 diagnostica antes de programar |
| RK2 | Android 16/17 crean el classloader con otra ruta (p. ej. un `LoadedApk` ya cacheado por `ActivityThread.mPackages` con un `ApplicationInfo` antiguo) | Los procesos invitados se matan al instalar (`killPackageAsUser`); verificar con la clase de split (R01.13) |
| RK3 | Apps con `isolatedSplits=true` necesitan `splitDependencies` | v1 no lo cubre; aviso en log; medir con WhatsApp/Gmail (5 APK, sin verificar si usan `isolatedSplits`) |
| RK4 | Espacio: GMS de 224 MB más el zip y su copia temporal | Descartar el zip original tras extraer; comprobación previa (R01.7) |
| RK5 | Tiempo de instalación y bloqueo del binder | Instalación fuera del hilo de UI; extracción por streaming |
| RK6 | El filtro de densidad/idioma deja la app sin un recurso | Siempre `en` y la densidad más cercana; log de lo descartado; opción futura "instalar todo" |
| RK7 | Verificación de firma de splits no disponible sin API oculta | R01.22 admite aviso; evaluar `android.util.apk.ApkSignatureVerifier` (sin verificar) |
| RK8 | Las `.so` en un split con `extractNativeLibs=false` esperan carga desde el zip | El box siempre extrae a `lib/` (comportamiento actual con el base); comprobar con WhatsApp |

## 8. Plan de pruebas

- **Unitarias (JVM):** `BundleDetectorTest`, `SafeUnzipTest`, `SplitSelectorTest`, `SplitManifestReaderTest`
  (con APK mínimos de fixture), `AbiUtilsTest`.
- **Instrumentadas:** `SplitInstallTest` (bundle, multiApk, invalid*, noSpace, badSignature),
  `SplitPersistTest`, `GenerateApplicationInfoTest`. Los fixtures se generan con bundletool a partir de una
  app propia (NotifProbe con un módulo de funcionalidad, strings localizados y dos densidades); **ningún APK
  de Google entra en el repositorio** ([ROADMAP](../ROADMAP.md)).
- **En dispositivo (las ejecuta el propietario, evidencia con `scripts/fase0/diag.sh`):**
  1. Android 16 con GMS (OPPO): clonar Play Store, Gmail y WhatsApp del sistema; abren sin `ClassNotFoundException`.
  2. Android 16 y 17 sin GMS: instalar `.apkm` de WhatsApp y de Gmail; arrancan hasta la pantalla inicial.
  3. Instalar el `GmsCore.apk` monolítico de MindTheGapps 16 (con la firma verificada según
     [fuentes-gms.md](../../investigacion/fuentes-gms.md)): comprueba que un APK único de 187 MB sigue
     instalándose (regresión, sin splits).
  4. Actualización del motor sobre una instalación previa (R01.17).
- **CI:** los tests JVM en el workflow existente; las comprobaciones de ADR-0006 (firma, alineación 16 KB,
  telemetría) no cambian.
- **Criterio de salida de la spec:** hito M1 de [ROADMAP](../ROADMAP.md) en su parte de splits.
