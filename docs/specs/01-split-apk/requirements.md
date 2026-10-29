# Spec 01: split APK y bundles — requisitos

Estado: Borrador

Contexto: [riesgos-android.md](../../fase0/riesgos-android.md) R1, [alcance-parches.md](../../fase0/alcance-parches.md) §1,
[ADR-0004](../../adr/0004-gms-real-como-ruta-principal.md). Medido en el OPPO CPH2841:
Play Store clonado falla con `ClassNotFoundException: com.android.vending.AssetBrowserActivity`
porque el classloader solo ve `base.apk`.

Sistemas: *el motor*, *el host*, *el box*, *la app invitada* (ver [README](../README.md)).

## Alcance

Dentro:
- Instalar en el box desde un bundle (`.apks`, `.apkm`, `.xapk`) o desde varios `.apk` sueltos (base + splits).
- Clonar al box apps del host que están instaladas con splits (`ApplicationInfo.splitSourceDirs`).
- Que la app invitada vea los splits en su `ApplicationInfo`/`PackageInfo`, en su classloader y en sus recursos.
- Persistencia, actualización y desinstalación coherentes.

Fuera:
- **APEX.** En MindTheGapps 17 el GMS viene dentro de `com.google.android.gmssystem.prodvic.apex`
  ([fuentes-gms.md](../../investigacion/fuentes-gms.md)). El motor **no** lee APEX: el propietario extrae
  `GmsCore.apk` a mano (`deapexer` o `fsck.erofs --extract`) y lo instala como APK suelto. Si el
  resultado es un APK único, no necesita esta spec.
- Descargar APKs, instalar GMS, desvío de providers (spec 03).
- Splits dinámicos bajo demanda (Play Feature Delivery): no hay Play dentro del box en esta spec.
- Ficheros OBB de `.xapk`: se ignoran con aviso (decisión en design.md, "Alternativas").

## Requisitos

### Instalación desde bundles y varios APK

**R01.1** WHEN el usuario instala un fichero `.apks`, `.apkm` o `.xapk` THE motor SHALL extraer sus APK,
identificar el base y los splits, e instalarlos como **un único paquete** en el usuario virtual elegido.
- Verificación: instalar un bundle de prueba generado con bundletool a partir de una app propia
  (NotifProbe con splits); aparece en "User 0" y se lanza. Test instrumentado `SplitInstallTest.bundle*`.

**R01.2** WHEN el usuario selecciona varios ficheros `.apk` a la vez (base + `split_config.*`) THE motor SHALL
instalarlos como un único paquete.
- Verificación: test instrumentado `SplitInstallTest.multiApk`; comprobación manual con el selector de ficheros.

**R01.3** THE motor SHALL decidir si una entrada es APK suelto, bundle o conjunto de APK por **su contenido**
(presencia de `AndroidManifest.xml` en la raíz del zip), no por la extensión ni por el nombre del `Uri`.
- Verificación: test unitario `BundleDetectorTest` con ficheros renombrados (`.zip`, sin extensión).

**R01.4** THE motor SHALL identificar el APK base como el único cuyo manifiesto no tiene el atributo `split`,
y el nombre de cada split por ese atributo, **sin depender** del manifiesto propio del bundle
(`toc.pb`, `info.json`, `manifest.json`).
- Verificación: test unitario `SplitManifestReaderTest` con APK de los tres formatos.

**R01.5** IF el conjunto no tiene base, tiene más de un base, mezcla nombres de paquete, o los splits declaran
otro `versionCode` que el base THEN THE motor SHALL abortar con un mensaje en `InstallResult.msg` y SHALL NOT
dejar ficheros, entrada en `Settings` ni procesos tocados.
- Verificación: tests `SplitInstallTest.invalid*` (4 casos) y comprobar que `data/app/<pkg>` no existe.

**R01.6** WHERE el bundle contiene splits de configuración para varias ABI, densidades o idiomas
THE motor SHALL instalar solo los compatibles con el dispositivo: la ABI primaria soportada por el box
(`BlackBoxCore.is64Bit()`), la densidad más cercana a la del dispositivo y los idiomas de la configuración
del sistema más `en`. Los splits no de configuración (`isFeatureSplit`) se instalan todos.
- Verificación: test unitario `SplitSelectorTest` con tablas de entrada; comprobar que
  `data/app/<pkg>/` de un bundle de 5 densidades solo contiene la elegida.

**R01.7** IF el espacio libre en el almacenamiento del host es menor que el tamaño descomprimido de lo que
se va a extraer más un margen del 10 % THEN THE motor SHALL abortar antes de extraer.
- Verificación: test con un `StatFs` simulado (`SplitInstallTest.noSpace`).

**R01.8** THE motor SHALL rechazar entradas de zip con `..`, ruta absoluta o enlaces simbólicos, y SHALL
limitar el número de entradas (512) y el tamaño descomprimido total (4 GiB) al extraer un bundle.
- Verificación: test unitario `SafeUnzipTest` con zip-slip y zip-bomb sintéticos.

**R01.9** WHEN la instalación termina, falle o no, THE motor SHALL borrar los ficheros temporales del bundle
(`cache/` del motor).
- Verificación: tras cada test, `BEnvironment.getCacheDir()` queda sin restos.

### Clonar desde el host

**R01.10** WHEN el usuario clona una app del host que tiene `splitSourceDirs` THE motor SHALL conservar
todos los splits en el `ApplicationInfo` que entrega a la app invitada.
- Verificación: clonar Gmail y WhatsApp (5 APK cada uno) en el Android 16 de referencia; sin
  `ClassNotFoundException`; paso (a)/(b) del [plan de pruebas](../../fase0/plan-pruebas.md).

**R01.11** WHEN el host actualiza una app clonada y cambia su `sourceDir` o cualquiera de sus `splitSourceDirs`
THE motor SHALL detectarlo al arrancar y reinstalar el clon sin perder los datos del usuario virtual.
- Verificación: actualizar WhatsApp en el host y reiniciar el box; el clon usa los splits nuevos
  (`dumpsys`/log `reInstallBySystem`).

### Lo que ve la app invitada

**R01.12** THE motor SHALL devolver a la app invitada, en `ApplicationInfo`, `sourceDir` = base,
`splitSourceDirs` y `splitPublicSourceDirs` con la misma longitud y el mismo orden que `splitNames`, y en
`PackageInfo` el campo `splitNames` coherente (no el `{}` fijo de `fake/frameworks/BPackageManager.java`).
- Verificación: app de prueba que vuelca esos campos (ampliar NotifProbe); test `GenerateApplicationInfoTest`.

**R01.13** THE motor SHALL construir el classloader de la app invitada con el base y todos los splits, en el
orden de `splitNames`.
- Verificación: la app de prueba carga por reflexión una clase que solo existe en un split de funcionalidad;
  abrir Play Store clonado (`AssetBrowserActivity`).

**R01.14** THE motor SHALL construir los `Resources` de la app invitada con los recursos del base y de todos
los splits instalados.
- Verificación: la app de prueba lee un string localizado y un drawable por densidad que solo están en
  config splits; sin `Resources$NotFoundException`.

**R01.15** WHEN un split de ABI contiene bibliotecas nativas THE motor SHALL extraerlas a `nativeLibraryDir`
junto con las del base, y SHALL decidir la compatibilidad de ABI mirando también los splits.
- Verificación: instalar WhatsApp por bundle (libs en `split_config.arm64_v8a`); arranca hasta la pantalla de
  bienvenida. Test `AbiUtilsTest.configSplit`.

### Persistencia, actualización y desinstalación

**R01.16** WHEN el proceso del servidor (`:black`) se reinicia THE motor SHALL recuperar la lista de splits de
cada paquete instalado sin reinstalar.
- Verificación: instalar, `am force-stop` del host, reabrir; la app invitada arranca (test `SplitPersistTest`).

**R01.17** THE motor SHALL seguir leyendo los paquetes instalados con versiones anteriores del motor (sin
splits) sin borrarlos ni exigir migración.
- Verificación: instalar un APK con la build actual de `aiab-main`, actualizar el motor con la build nueva
  encima; la app sigue en la lista y arranca.

**R01.18** WHEN se reinstala o actualiza un paquete THE motor SHALL reemplazar base, splits y bibliotecas
completos; SHALL NOT quedar splits ni `.so` del paquete anterior.
- Verificación: instalar bundle A (3 splits), luego bundle B (1 split); `data/app/<pkg>` contiene 1 split.

**R01.19** WHEN se desinstala un paquete THE motor SHALL borrar base, splits, bibliotecas y el fichero de
metadatos de splits.
- Verificación: tras desinstalar, `data/app/<pkg>` no existe.

### Interfaz de la app

**R01.20** WHEN el usuario pulsa "instalar desde archivo" THE host SHALL permitir elegir varios ficheros y
aceptar bundles y APK, y SHALL mostrar el resultado o el error de `InstallResult.msg`.
- Verificación: paso manual en el Android 16 de referencia con un `.apkm` y con 3 `.apk` sueltos.

**R01.21** WHERE un APK no es el base de ningún conjunto (un split suelto) THE host SHALL informar de que falta
el base en lugar de fallar con "parser apk error".
- Verificación: instalar solo `split_config.xxhdpi.apk`; mensaje legible.

### Firma

**R01.22** WHEN es posible leer la firma de cada split THE motor SHALL comprobar que coincide con la del base y
abortar si no coincide; IF no es posible THEN THE motor SHALL registrar un aviso y verificar solo el base.
- Verificación: test con un split firmado con otra clave (`SplitInstallTest.badSignature`), si el
  mecanismo elegido en design.md lo permite (sin verificar).
