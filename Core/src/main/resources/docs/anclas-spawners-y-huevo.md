# Anclas, spawners y el huevo-ficha

## El huevo como ficha del mob

Un huevo custom **no se gasta**. Es la ficha de un mob: sirve para invocarlo, recogerlo y
volver a desplegarlo.

1. **Huevo nuevo** (recien crafteado): no esta ligado a nada.
2. Al **colocarlo** (clic derecho sobre un bloque) nace el mob, y el huevo queda **ligado**
   a el. **No se consume.**
3. **Golpear al mob con su propio huevo**: se recoge. El mob desaparece y vuelve dentro del
   huevo, guardado.
4. **Volver a colocar** ese huevo: el mob se despliega de nuevo, en el mismo vinculo.
5. Si el mob **muere**, el vinculo se borra y el huevo queda **inerte**: apunta a la nada, y
   tratar de colocarlo avisa *"Este huevo ya no sirve: el mob que representaba ya no existe."*

No hay que ir a buscar el huevo por el mundo para desactivarlo: muerto el mob, el huevo
queda inservible solo. Revisar inventarios o cofres sirve para quitarselo de las manos,
pero no hace falta para que quede inerte.

El **contenido de la ficha** queda registrado en `player-mobs.yml`, que **cuenta los mobs
anclados a un bloque aunque su chunk este descargado** — un conteo que solo mirase las
entidades cargadas se saltaria el tope desplegando mobs por bloques repartidos.

## Ancla y leash

```yaml
leash:
  anchor: owner
  max-distance: 10.0
  teleport-distance: 24.0
  return-speed: 1.0
```

- `anchor: owner` — el mob se mantiene pegado a su dueno.
- `anchor: point` — el mob se queda fijo al bloque donde aparecio.
- `max-distance` — radio que puede alejarse. `0` = sin limite.
- `teleport-distance` — si se aleja mas, se teletransporta de vuelta al ancla.
- `return-speed` — velocidad del retorno.

**Los mobs de servidor quedan siempre anclados a su punto**: no se les aplica `anchor`. 

## Ventana de abandono

Un mob anclado a su dueno **solo lo sigue a un mundo habilitado**. Si el dueno se va a un
mundo excluido en `worlds.enabled`, o lo deja demasiado lejos, el mob no puede volver a su
lado — y entra la **ventana de abandono**:

1. Se pide un **ticket de chunk** en su posicion y el dueno recibe un **aviso por chat**.
2. A los `recall.mob-seconds` (por defecto **60 s**) el mob **se destruye** y su vinculo se
   borra: su huevo queda inerte.
3. A los `recall.chunk-seconds` (por defecto **120 s**) se **suelta el ticket** y la chunk
   vuelve a lo normal.

Si el dueno **vuelve antes**, la cuenta se cancela y el mob se conserva.

Dos cosas que conviene tener claras:

- **Estar desconectado no es abandono.** Al volver, tus mobs siguen donde estaban.
- Los mobs anclados a un **punto** no siguen a nadie: no se abandonan.

El radio sale de la `simulation-distance` del mundo (`recall.radius: 0.0`); con un valor
mayor se fija a mano. Configuracion completa en `configuracion.md`.

## Spawners

Un mob de servidor con `respawn-seconds` mayor que 0 convierte su punto de aparicion en un
**spawner**:

```yaml
respawn-seconds: 30   # reaparece 30 s despues de morir
```

- `0` = una sola vida. El mob no vuelve.
- El estado del spawner vive en **`data/spawners.yml`**, aparte de la entidad, porque debe
  **sobrevivir a la muerte del mob**.
- El registro distingue "murio" (y hay que reanimarlo) de "chunk descargado" (sigue vivo),
  asi que un reinicio del servidor no duplica mobs.

### Quitar un spawner

Desde el juego, `/customcore remove [radio]` retira los mobs custom cercanos y, si eran de
un spawner, **borra tambien el spawner**: dejan de reanimarse.

Para limpiarlos de verdad —desde la consola, sin caminar hasta el sitio y aunque su chunk
este descargado— estan los subcomandos:

```
/customcore spawner list                 ver los que hay, con su id
/customcore spawner remove <id>          borrar uno (y su mob vivo)
/customcore spawner removeall [mob]      borrar todos, o los de un mob
/customcore spawner reload               releer el archivo sin reiniciar
```

Ojo: `/customcore kill <id>` **no** sirve para esto — mata los mobs, pero si son de un
spawner **vuelven**. Y editar `data/spawners.yml` a mano no basta con `/customcore
reload`: hace falta reiniciar, o usar `spawner reload`.

## Que se guarda y donde

| Dato | Donde vive |
|---|---|
| Definicion, dueno, team, punto de aparicion | PersistentDataContainer de la entidad |
| Ficha del huevo (vinculo) | `player-mobs.yml` |
| Puntos de spawner | `data/spawners.yml` |
| Color de nombre y brillo | `styles.yml` |

El vinculo vive en el PDC de la entidad, asi que sobrevive reinicios sin archivo aparte.
Los spawners son la excepcion: su punto debe sobrevivir a la muerte del mob.
