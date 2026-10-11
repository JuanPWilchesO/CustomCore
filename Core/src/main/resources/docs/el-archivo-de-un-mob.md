# El archivo de un mob

Todo mob vive en `mobs/<archivo>.yml`. Solo `id` y `type` son obligatorios.

## Identidad

| Campo | Valores | Por defecto | Notas |
|---|---|---|---|
| `id` | texto | nombre del archivo | identificador unico |
| `category` | `player` \| `server` \| `mount` | `player` | ver abajo |
| `display-name` | texto con `&` | el `id` | los codigos `&` se traducen a color |
| `type` | tipo de entidad | — | `ZOMBIE`, `SKELETON`, `VILLAGER`, `RAVAGER`… cualquier entidad que sea un `Mob` |
| `glow` | `true` \| `false` | `false` | contorno visible |
| `lore` | lista de textos | vacio | se muestra en el huevo |
| `egg` | material | — | huevo base de la receta |

**`player`** — se invoca con un huevo crafteado, tiene dueno y lo protege.
**`server`** — no tiene dueno, pertenece a una faccion, y puede ser un spawner.
**`mount`** — un caballo o similar que el jugador monta, con efectos y cupo propios.
Ver `monturas.md`.

## Atributos

```yaml
attributes:
  health: 40.0
  damage: 5.0
  speed: 0.25
  follow-range: 24.0
```

Claves admitidas: `health`, `max-health`, `damage`, `attack-damage`, `speed`,
`movement-speed`, `armor`, `armor-toughness`, `attack-speed`, `attack-knockback`,
`knockback-resistance`, `follow-range`, `gravity`, `jump-strength`, `step-height`,
`scale`, `max-absorption`, `movement-efficiency`, `water-movement-efficiency`.

## Equipamiento

```yaml
equipment:
  main-hand:
    material: IRON_AXE
    name: '&7Espada del guardia'
    lore:
      - '&8Forjada en el puesto.'
    unbreakable: true
    glint: true
    drop-chance: 0.0
    enchants:
      sharpness: 3
      excellentenchants:regrowth: 2
  boots:
    material: NETHERITE_BOOTS
    enchants:
      protection: 4
```

Slots: `helmet`, `chestplate`, `leggings`, `boots`, `main-hand`, `off-hand`.
Se aceptan tambien los nombres de Bukkit (`head`, `chest`, `CHEST`…).

`drop-chance` va de `0.0` (nunca suelta) a `1.0` (siempre).

**Un slot tambien puede salir del catalogo** de objetos con nombre — es la via para
equipar algo con NBT, como un encantamiento de otro plugin:

```yaml
equipment:
  main-hand:
    item: espada_del_sargento    # nombre guardado con /custommobs item save
    drop-chance: 0.0
```

Con `item:` el objeto manda **tal cual** y conserva su NBT; solo se le respeta
`drop-chance`, que es propiedad del mob y no del objeto.

## Encantamientos

Los nombres se resuelven en este orden:

| Escribes | Se aplica |
|---|---|
| `sharpness` | `minecraft:sharpness` (vanilla) |
| `minecraft:sharpness` | ese namespace tal cual |
| `excellentenchants:regrowth` | encantamiento custom de ExcellentEnchants |

Sin namespace se busca primero en vanilla y luego en ExcellentEnchants. Los
encantamientos custom no se comprueban al cargar: si escribes mal un id, el mob carga
igual y el encantamiento simplemente no aparece. Los ids validos se listan con
`/custommobs enchants excellentenchants`.

## Objetivos contra jugadores

```yaml
targets:
  players: defensive
```

| Modo | Contra jugadores |
|---|---|
| `never` | nunca. Mob de apoyo puramente PvE |
| `enemies` | solo si Teams los marca enemigos |
| `non-allies` | todo jugador que no sea de su team ni aliado |
| `defensive` | neutral hasta que agredan a su bando |

Sin este campo, se hereda `targeting.player-mode` del config (`defensive` por defecto).
Los monstruos hostiles de vanilla y los mobs de facciones enemigas se atacan igual.

**La regla que no se rompe**: el dueno, los companeros de team y los aliados **nunca**
son objetivo, ni siquiera si ellos agreden primero.

## Faccion y actitud (solo `category: server`)

```yaml
faction: orcos
attitude: hostile
```

| `attitude` | Comportamiento frente a jugadores |
|---|---|
| `hostile` | ataca jugadores a la vista |
| `neutral` | no ataca salvo que lo agredan |
| `defender` | nunca ataca jugadores; persigue a los monstruos que los amenazan |

En un mob de servidor, la relacion con jugadores la manda `attitude`; `targets.players`
es para los mobs de jugador. Ver `facciones-equipos-y-combate.md`.

## Ancla y leash

```yaml
leash:
  anchor: owner
  max-distance: 10.0
  teleport-distance: 24.0
  return-speed: 1.0
```

- `anchor`: `owner` (pegado a su dueno) o `point` (fijo al bloque donde aparecio).
- `max-distance`: radio que puede alejarse. `0` = sin limite.
- `teleport-distance`: si se aleja mas, se teletransporta de vuelta.

Los mobs de `category: server` quedan **siempre** anclados a su punto: no se les aplica `anchor`.

### Ancla a un bloque: zona cargada

Un mob de jugador con `anchor: point` vive fijo en su bloque. Para que no desaparezca ni
deje de contar cuando no hay jugadores cerca, su zona se mantiene **cargada**:

```yaml
# Radio en chunks que se mantiene cargado alrededor del bloque.
# Si no se pone, se usa el global (point.chunk-radius del config). 0 = desactivado.
chunk-radius: 4
```

Ojo con el coste: es un cuadrado de `(2*radio+1)^2` chunks. Con radio 4 son **81 chunks**
por mob. Solo se suelta cuando el mob se recoge con su huevo; si luego se coloca en otro
sitio, se cargan los chunks del lugar nuevo.

## Apariencia, sonidos y resto

```yaml
disguise:
  enabled: true
  type: player
  skin: NombreDeJugador
  skin-url: ''
  show-name: true

sounds:
  ambient:
    sound: entity.zombie.ambient
    volume: 1.0
    pitch: 0.7
    interval-seconds: 10
  hurt:   { sound: entity.zombie.hurt,  volume: 1.0, pitch: 0.7 }
  death:  { sound: entity.zombie.death, volume: 1.0, pitch: 0.7 }
  attack: { sound: entity.zombie.attack, volume: 1.0, pitch: 0.7 }

burn-in-daylight: false
ai: true
respawn-seconds: 30
```

- `disguise.type`: `player` usa `skin` (nombre de jugador) o `skin-url`.
- `burn-in-daylight`: por defecto `false` en mobs de servidor y `true` en los de jugador.
- `ai: false`: el mob se queda quieto en su puesto. Util para figurantes.
- `respawn-seconds`: solo mobs de servidor. `0` = una sola vida. Con un valor mayor,
  el punto funciona como **spawner** de ese mob.

## Un mob custom no se modifica a mano

El mob cambia **solo** por lo que el plugin permite: el estilo de su dueno, los items de
mejora, su huevo. Todo lo demas lo decide su definicion. Se bloquea, con aviso en el chat:

| Intento | Resultado |
|---|---|
| Renombrarlo con una **etiqueta** (`name tag`) | bloqueado |
| Ponerle **silla** o **armadura** a mano | bloqueado |
| Llevarselo con una **correa** (`lead`) | bloqueado |
| **Alimentarlo** o criarlo | bloqueado |
| Equiparlo con un **dispensador** | bloqueado |
| Mover la silla o la armadura en el **inventario** de una montura | bloqueado |

Con la mano vacia **si** se puede montar una montura: solo se bloquea el clic cuando trae
puesto uno de esos objetos.

## Montura (`category: mount`)

Una montura anade una seccion `mount:` con su variante, su armadura, sus efectos constantes
y su paso helado, y tiene **su propia cuenta de cupo**. Todo esta en `monturas.md`.

## Aparicion natural (`spawn`)

Solo para mobs de `category: server`: pide que el mob **brore solo** por el mundo. Sin esta
seccion, un mob de servidor nunca aparece por su cuenta.

```yaml
spawn:
  natural: true
  chance: 0.6          # probabilidad por intento (0..1); por defecto 0.1
  group:
    min: 1
    max: 3
  time: any            # any | day | night
  light: { min: 0, max: 15 }
  y: { min: 0, max: 320 }
  distance:
    min: 16.0          # distancia al jugador (por defecto 24)
    max: 48.0          # (por defecto 96)
  cap: 6               # tope de este mob vivo a la vez (por defecto 20)
  worlds: [world]      # opcional: solo en estos mundos
  biomes: [plains]     # opcional: solo en estos biomas
```

| Clave | Que hace | Por defecto |
|---|---|---|
| `natural` | activa la aparicion; sin esto no hay | `false` |
| `chance` | probabilidad por intento, de `0.0` a `1.0` | `0.1` |
| `group.min` / `group.max` | tamano del grupo que brota | `1` / el minimo |
| `time` | momento del dia: `any`, `day`, `night` | `any` |
| `light.min` / `light.max` | rango de luz del bloque | `0` / `15` |
| `y.min` / `y.max` | rango de altura | `0` / `320` |
| `distance.min` / `distance.max` | distancia al jugador, en bloques | `24.0` / `96.0` |
| `cap` | tope de este mob natural vivo a la vez | `20` |
| `worlds` | lista de mundos permitidos | todos |
| `biomes` | lista de biomas permitidos | todos |
| `allowed-region` | region de WorldGuard donde SI aparece | vacia: en cualquier parte |
| `deny-regions` | lista de regiones donde NO aparece | vacia: ninguna vetada |

### Region del spawneo (WorldGuard)

```yaml
spawn:
  natural: true
  allowed-region: parque      # si esta, SOLO aparece aqui
  deny-regions:
    - zona_pvp
    - spawn
```

- `allowed-region` **vacia** = aparece en cualquier parte. Con una region, **solo** ahi.
- `deny-regions` **vacia** = ninguna vetada. Con regiones, **no** aparece en ellas.
- Si el punto cae en varias regiones nombradas y se contradicen, **manda la de mayor
  prioridad de WorldGuard**. Asi la regla mas especifica gana, que es como se piensan las
  regiones.

Es una integracion **opcional**: sin WorldGuard instalado, todo punto vale y el plugin
funciona exactamente igual. Los nombres de region son los de WorldGuard (`/rg list`).

El ritmo y el tope global salen del config (`spawning`). Un mob natural es de **una sola
vida** y **no deja spawner**.

## Convivencia y dano

```yaml
# No molesta al pueblo: no apunta a aldeanos ni a golems, y los golems no lo apuntan a el.
village-friendly: true

# Causas de dano que ignora. Vale en cualquier categoria.
immune:
  - fall
  - potion
```

| Clave | Que hace | Por defecto |
|---|---|---|
| `village-friendly` | defiende al pueblo: no ataca a aldeanos ni golems, los golems no lo apuntan a el, y va a por quien le pegue a un aldeano | `false` |
| `immune` | lista de causas de dano que ignora | vacia |
| `attacks-monsters` | ataca a los monstruos hostiles vanilla (`category: server`) | `false` |
| `transformable` | si el juego puede convertirlo (aldeano -> bruja, zombi -> ahogado...) | `true` |

### Transformaciones

```yaml
transformable: false
```

El juego transforma mobs todo el tiempo, y para un mob custom eso es **perder la definicion**:
un aldeano mordido se vuelve zombi aldeano, a uno le cae un rayo y sale bruja, un zombi que se
ahoga pasa a ahogado, un cerdo con un rayo se vuelve piglin zombificado...

Con `transformable: false` **ninguna** de esas conversiones le pasa. Por defecto se transforma
como cualquier otro mob, que es como se ha comportado siempre — asi que nada de lo que ya
tenias cambia.

### Atacar monstruos

```yaml
attacks-monsters: true
```

Solo para mobs de `category: server`. Sin esta clave, un mob de servidor **solo** ataca a los
monstruos vanilla si su `attitude` es `defender` —el defensor persigue a los que amenazan la
zona—. Con `true`, los ataca cualquiera, sea `neutral` u `hostile`: util para un mob neutral
que se defiende, o para uno hostil que ademas limpia la zona.

Causas utiles de `immune`: `fall` (dano de caida), `potion` (atajo de `magic`, el dano de
las pociones), `fire`, `lava`, `drowning`, `explosion`, `projectile`, `contact`... Se escribe
el nombre de la causa de Bukkit en minusculas; lo que no se reconoce se avisa y se descarta.

### Que hace exactamente `village-friendly`

| Comportamiento | Sin la clave | Con la clave |
|---|---|---|
| Ataca a aldeanos o golems | si | **no** |
| Un golem lo ataca a el | si | **no** |
| Si le pegan a un aldeano cerca | lo ignora | **va a por el agresor** |

Esa ultima es mas radical que un golem: reacciona **cualquier** mob amistoso que lo vea, sea
cual sea su `attitude` —incluso un `neutral` o un `defender`—, porque la agresion pesa mas que
la actitud. Y cuenta igual si le pegan al aldeano con una flecha. La hostilidad dura lo que
diga `aggro-duration-seconds` del config y se percibe dentro de `aggro-watch-radius`.

> **Aviso sobre el miedo de los aldeanos.** Que el mob **no ataque** a aldeanos y golems, que
> los golems **no lo ataquen** y que **defienda** al pueblo se arregla con esta clave. Pero que
> los aldeanos **no huyan** no: eso lo decide el **tipo de entidad**. Huyen de zombis e illagers
> (y del ravager y los vex); de un **esqueleto no huyen**. Para un mob que conviva con aldeanos,
> usa la familia **esqueleto** — y si quieres otra apariencia, disfrazalo: el sistema de disfraces
> admite cualquier tipo de mob.

## Crafteo

```yaml
recipe:
  extras:
    - DIAMOND
    - NETHER_STAR
  amount: 1
```

- Los `extras` se suman al huevo base (`egg`).
- `amount: 0` **desactiva el crafteo**: el mob solo se da con `/custommobs give`.
- Sin bloque `recipe`: por defecto huevo + diamante.
- Dos mobs con los mismos ingredientes colisionan: el segundo se omite con un aviso,
  porque el cliente no podria distinguirlos. (`/custommobs reload` lo reporta.)

## Skills y drops

- **Skills**: ver `skills.md`.
- **Drops**: ver `drops-y-objetos.md`.
