# Desarrollo

Guia para compilar, entender y publicar CustomCore.

## Requisitos

- **JDK 25**. `paper-api` 26.3 viene en bytecode de Java 25 y un javac anterior no lo lee.
- **Maven 3.9+**.
- **Paper 26.3** para probar. Es la unica version soportada.

## Estructura

- `API/` — interfaces publicas (`CustomMobsApi`). Se puede consumir como dependencia.
- `Core/` — el plugin: `com.juanp.custommobs`.

Paquetes principales:

- `mob/` — `MobLoader` (YAML -> `MobDefinition`), `MobRegistry`, `MobService` (spawn, dueno, cupos).
- `skill/` — `SkillService`, `SkillSpec`, `ProjectileService`, `ArrowSelection`.
- `cc/` — `CcService` (stun, root, silence, disarm, escudo) y `CcListener`.
- `ui/` — `StatusService` (boss bars y titulos) y `StatusListener`.
- `effect/` — `effects.yml`, pociones, mecanicas y PDC.
- `item/` — catalogo NBT, recetas, bloqueos de faccion/clase y recogida de flechas.
- `craft/`, `drop/`, `upgrade/`, `book/`, `team/`, `group/`, `disguise/` — sistemas opcionales.

Sin NMS: solo API de Paper. La IA usa `MobGoals` cuando esta disponible y cae a un task
periodico cuando no.

## Compilar

```bash
mvn -q -DskipTests package
```

Salida: `Core/target/CustomCore-<version>.jar`. La API sale de `API/target/`.

## Versionado

- Desarrollo: `X.Y.Z-SNAPSHOT`.
- Publicacion: `X.Y.Z`.

La version vive en los tres `pom.xml` (`pom.xml`, `Core/pom.xml`, `API/pom.xml`).
`plugin.yml` la toma de `${project.version}`.

## Publicar una release

1. Probar en un servidor Paper 26.3 con los plugins opcionales instalados.
2. Actualizar `README.md`, `docs/`, `tutorials/` y los comentarios de la config inicial.
3. Subir la version en los tres `pom.xml` (quitar `-SNAPSHOT`).
4. `mvn -q -DskipTests package`.
5. Verificar el jar: `unzip -p Core/target/CustomCore-X.Y.Z.jar plugin.yml | grep version`
   y que lleve `docs/` y `tutorials/`.
6. Copiar el jar a `plugins/`, retirar la version anterior y reiniciar.
7. `git tag vX.Y.Z`, `git push origin master --tags` y crear la release en GitHub con el jar.

## Claves PDC

- `customcore:item_catalogo` — nombre del objeto en el catalogo.
- `customcore:item_faccion` / `item_clase` — restricciones que viajan con el objeto.
- `customcore:item_stun_free` — se puede usar bajo un control.
- `customcore:item_mod` — modificaciones prohibidas.
- `customcore:flecha_cargada` — tipo de flecha cargada por el jugador.
- `customcore:potion` — id de la pocion del plugin.

## Convenciones

- Comentarios y documentacion en espanol; mensajes de comando en gris `&8`.
- Nada de clases hardcodeadas: la clase de flechas sale de `items.arrows-class`.
- No sobrescribir archivos del usuario (`config.yml`, `mobs/`, `items/`, `skills/`).
- Probar en servidor antes de publicar.

## Debug

- `debug: true` en `config.yml` para trazas de skills y objetivos.
- `debug-packets: true` solo para diagnosticar el brillo de facciones.
