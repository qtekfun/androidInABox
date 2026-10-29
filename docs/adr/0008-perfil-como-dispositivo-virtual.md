# ADR-0008: Cada perfil es un dispositivo virtual completo

- Estado: aceptada (decisión del propietario, 2026-10-29)
- Fecha: 2026-10-29

## Contexto

Hasta ahora los perfiles del box solo separan los datos de las apps. El APK de cada
paquete se guarda una sola vez y lo comparten todos los perfiles, y todos presentan la
misma identidad de dispositivo. Consecuencias:

- GMS real y microG no pueden convivir en perfiles distintos, porque usan el mismo
  nombre de paquete (ADR-0007).
- Cambiar los servicios de Google en un perfil afecta a los demás.
- Las cuentas de perfiles distintos aparecen ante Google como el mismo teléfono.
- La capa del fabricante se cuela dentro del box. Por ejemplo, la extensión del
  `ContentResolver` de ColorOS filtra las consultas a los providers de GMS.

Las apps de GMS para Huawei resuelven esto presentando un dispositivo completo a las
apps que alojan.

## Decisión

Cada perfil se comporta como un teléfono independiente:

1. **Apps propias.** El APK, los splits y las librerías nativas se guardan por perfil y
   paquete. Cada perfil puede tener su propia versión de una app, y GMS real en uno y
   microG en otro.
2. **Identidad propia.** Cada perfil tiene un `android_id`, un id de GSF/checkin y un
   número de serie virtual propios, generados una vez y persistentes. El IMEI sigue
   siendo nulo: no se inventan identificadores de hardware.
3. **Modelo del teléfono real.** `Build` muestra el fabricante y el modelo reales. El
   fingerprint certificado solo se aplica a las apps que lo necesiten, con la opción por
   app de la spec 08.
4. **Aislamiento de la capa del fabricante.** El box neutraliza los hooks del fabricante
   que cambian el comportamiento del framework para las apps invitadas, como el filtro
   de ColorOS.

## Consecuencias

- Más espacio en disco: GMS ocupa unos 220 MB por perfil. Para no duplicarlo, se
  reutiliza una copia en disco cuando el APK es idéntico (mismo hash).
- Cambia el formato de persistencia. Los datos actuales se migran con `PersistFormat`,
  sin borrar el box.
- La elección entre GMS y microG (ADR-0007) pasa a ser independiente en cada perfil, sin
  desinstalar nada en los demás.
