# ADR-0002: Toolchain de compilación del motor

- Estado: aceptada
- Fecha: 2026-10-29

## Contexto

El upstream (`89b5983`) usa AGP 8.13.2, Gradle 8.14.5, Kotlin 1.9.23, NDK 29.0.13846066 y
`sourceCompatibility 21`. Su README pide JDK 17, que no basta. Con JDK 25 falla al
configurar (`> 25.0.4.1`), porque el plugin de Kotlin 1.9.23 no reconoce esa versión.

## Decisión

- Compilar con **JDK 21 LTS** (Temurin) y NDK 29.0.13846066. Fijar `platforms;android-35`
  y `build-tools;35.0.0` mientras no se suba compileSdk.
- Generar solo **arm64-v8a** en las builds de prueba. Los dispositivos objetivo con
  Android 16 o 17 son de 64 bits, y algunos ya no ejecutan 32 bits (el OPPO de pruebas
  solo declara arm64-v8a).
- Verificar la alineación de 16 KB en cada build: `zipalign -c -P 16` y `llvm-readelf -lW`
  sobre las `.so`.

## Consecuencias

- La CI necesitará JDK 21 explícito; la de serie de las distribuciones recientes no vale.
- Actualizar Kotlin (2.x) y compileSdk a 36 queda para la fase 1, junto con los parches
  del motor.
