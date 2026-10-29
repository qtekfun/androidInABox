# ADR-0007: Elegir GMS real o microG en cada perfil

- Estado: aceptada (decisión del propietario, 2026-10-29)
- Fecha: 2026-10-29

## Contexto

El GMS real avanza dentro del box (Play Store abre, el checkin pasa), pero cada paso
depende de privilegios de sistema que el box tiene que emular. microG está diseñado para
funcionar sin privilegios de sistema.

## Decisión

En Ajustes → Servicios de Google, cada perfil puede usar:

1. **Google (GMS real)**, el de ADR-0004. Sigue siendo la ruta principal.
2. **microG** (GmsCore de microG, su Companion y, opcionalmente, GsfProxy).

Funcionamiento:

- Los APK los aporta el usuario; el motor no los incluye ni los descarga.
- Para microG, el motor implementa la suplantación de firma dentro del box. Si un paquete
  declara la meta-data `fake-signature` y el perfil tiene microG activado, las apps de
  ese perfil ven la firma declarada. Nunca afecta al sistema real ni a otros perfiles.
- Un perfil tiene una sola de las dos opciones. Cambiar de una a otra desinstala la
  anterior en ese perfil, con confirmación.

## Consecuencias

- Hay dos caminos que probar y mantener.
- La suplantación de firma solo se aplica a paquetes que la declaran y en perfiles con
  microG. Así se acota el riesgo de que una app maliciosa se haga pasar por Google.
