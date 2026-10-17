# API para otros plugins y problemas frecuentes

## API

Se registra un servicio `CustomCoreApi` en el `ServicesManager`:

```java
CustomCoreApi api = getServer().getServicesManager().load(CustomCoreApi.class);

api.definitionIds();                              // ids de mobs cargados
api.isCustomMob(entity);                          // es un mob gestionado?
api.viewOf(entity);                               // vista de solo lectura
api.spawn("guardia", location, ownerPlayer);      // invoca uno
```

El servicio vive en el modulo **API**, que va empaquetado dentro del jar del **Core**.

## Problemas frecuentes

**Un mob no aparece al usar el huevo.**
Revisa en este orden: (1) que tengas `customcore.player`; (2) que no hayas llegado a tu
cupo — `/customcore cuota`; (3) que la receta sea la que crees.

**"Este huevo ya no sirve."**
Es lo esperado: el mob que representaba murio, asi que el vinculo se borro. Ese huevo es
inservible para siempre. Craftea otro.

**"Ese mob ya esta desplegado."**
El huevo esta ligado a un mob vivo. Para recogerlo, **golpea al mob con el huevo**;
colocarlo otra vez no hace nada mientras siga desplegado.

**Uso el huevo en un mundo excluido y no pasa nada.**
Es lo esperado: los mundos fuera de `worlds.enabled` bloquean tambien la invocacion, no
solo la gestion. Veras el aviso `El plugin no funciona en este mundo`.

**"Jugador no encontrado" al usar `give`.**
`getPlayerExact` solo encuentra jugadores **conectados**.

**Los ids duplicados de mob se ignoran.**
Dos mobs con el mismo `id` (o el mismo archivo renombrado) colisionan. El log lo avisa.

**El crafteo no aparece o avisa de colision.**
Dos mobs con los mismos ingredientes: el cliente no puede distinguirlos. Cambia
`recipe.extras` en uno de ellos.

**Un encantamiento no se aplica.**
Los encantamientos custom no se validan al cargar. Comprueba el id real con
`/customcore enchants excellentenchants`.

**Quedo bloqueado sin tener mobs.**
Tu cupo incluye mobs anclados en chunks descargados. Si un mob desaparecio sin pasar por
muerte ni retirada (un plugin externo, un corte), la cuenta queda alta:
`/customcore cuota recontar <jugador>`.

**Cambie los permisos por defecto y no aplican.**
`plugin.yml` solo se lee al arrancar. Requiere reinicio del servidor.

**El mob no hace nada.**
Mira si tiene `ai: false` (figurante quieto) o si `leash.max-distance` es 0 y el ancla
esta lejos.

## Archivos que genera

| Archivo | Que guarda |
|---|---|
| `config.yml` | configuracion general |
| `factions.yml` | relaciones entre facciones |
| `mobs/*.yml` | un archivo por mob |
| `player-mobs.yml` | cupo de mobs de jugador (incluye los de chunks descargados) |
| `styles.yml` | color de nombre y brillo elegidos por jugador y por team |
| `items/<nombre>.yml` | catalogo de objetos con NBT, uno por nombre |
| `data/spawners.yml` | puntos de aparicion que sobreviven a la muerte del mob |
| `docs/` | esta documentacion |
| `tutorials/` | los tutoriales paso a paso |
