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
    item: espada_del_sargento    # nombre guardado con /customcore item save
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
`/customcore enchants excellentenchants`.

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

> **Ojo con `defender`:** los jugadores pasan a ser **de su bando**, así que el mob tampoco
> recibe daño de ellos. Sus golpes se anulan en silencio: el mob parece invulnerable. Si
> quieres que no ataque pero que sí se le pueda pegar, usa `neutral`.

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

Si el mob define **al menos un** sonido, su cuerpo vanilla queda **enmudo**: ya no suelta los
suyos (grito, ambiente, pasos). Los que suenan son los del yml, y **solo** esos.

Consecuencias que conviene saber:

- El mob **no tiene sonido de pasos** salvo que lo pongas: no hay `step` en el yml.
- Los sonidos que otros plugins hagan con la entidad (o su muerte vanilla) tampoco se oyen.
- Los del yml se mandan a cada jugador a **32 bloques**; mas lejos no se oyen.
- Un mob que **no** defina `sounds:` suena como cualquier bicho vanilla.

- `disguise.type`: `player` usa `skin` (nombre de jugador) o `skin-url`.
- `burn-in-daylight`: por defecto `false` en mobs de servidor y `true` en los de jugador.
- `ai: false`: el mob se queda quieto en su puesto. Util para figurantes.
- `respawn-seconds`: solo mobs de servidor. `0` = una sola vida. Con un valor mayor,
  el punto funciona como **spawner** de ese mob.

## Modelo 3D (`model`, BetterModel)

Un mob puede llevar un modelo de [BetterModel](https://modrinth.com/plugin/bettermodel)
encima: el mob sigue siendo el mismo (misma vida, mismo dano, mismos objetivos), pero el
cliente ve el modelo en vez del cuerpo vanilla.

```yaml
model:
  enabled: true
  name: guardia_espada
  hide-body: true
  hide-equipment: true
  hide-glow: false
  scale: 1.0
  damage-animation: true
  damage-tint: true
  seat: true
  animations:
    idle: idle
    walk: { name: walk, speed: 1.0 }
    attack: { name: attack, once: true }
    hurt:   { name: hurt,   once: true }
    death:  { name: death,  once: true, end: 5 }
    spawn:  spawn
```

| Clave | Valores | Por defecto | Notas |
|---|---|---|---|
| `enabled` | `true` \| `false` | `true` | `false`: el mob se ve vanilla |
| `name` | texto | — | nombre del modelo en `plugins/BetterModel/models/`; **obligatorio** |
| `hide-body` | `true` \| `false` | `true` | oculta el cuerpo de la entidad |
| `hide-equipment` | `true` \| `false` | `true` | oculta el equipo que lleva |
| `hide-glow` | `true` \| `false` | `false` | deja ver el brillo (por ejemplo, el de faccion) |
| `scale` | numero | el del modelo | escala del modelo |
| `damage-animation` | `true` \| `false` | `true` | animacion de dano del propio modelo |
| `damage-tint` | `true` \| `false` | `true` | tinte rojo al recibir dano |
| `sight-trace` | `true` \| `false` | `false` | solo lo dibuja a quien tiene linea de vision. **Apagado por defecto**: encendido, BetterModel gira el modelo hacia el jugador por su cuenta y el mob parece vivo aunque su IA este parada (pasa con un aturdido) |
| `show-name` | `true` \| `false` \| `auto` | `auto` | quien pinta el nombre, ver abajo |
| `seat` | `true` \| `false` | `true` | usar el hueso `p_...` del modelo como asiento (`category: mount`) |
| `animations` | mapa | vacio | animaciones por evento, ver abajo |

**Si el mob tambien tiene `disguise:` activo, gana el disfraz** y el modelo se ignora (se
avisa una vez en el log). Es a proposito: un disfraz y un modelo encima se pelean por el
mismo cliente.

**Sin BetterModel instalado** la seccion no hace nada: el mob se ve como siempre y el
plugin sigue funcionando. Si el plugin esta pero el modelo no existe, pasa lo mismo y el
log avisa (y `/customcore model validate` los lista).

**El modelo tiene que estar cargado** en `plugins/BetterModel/models/`. CustomCore no anade
ni quita modelos: solo los usa por nombre. Anadir un `.bbmodel` cambia el pack de BetterModel,
asi que si lo sirves con AutoPack hay que regenerarlo y recargarlo.

**El cuerpo solo se oculta si el modelo tiene hitbox.** Y aqui esta el detalle que se pierde
en casi todas las guias: BetterModel **no lee el tag de bloque de Blockbench**, lo deduce del
**nombre del hueso**. El nombre se parte por `_` y cada palabra se busca en esta tabla:

| Lo que hace | Abreviatura en el nombre |
|---|---|
| Hitbox | `b` u `ob` |
| Asiento | `p` |
| Cabeza | `h` (o `hi`, con los hijos) |
| Placa con el nombre | `mtag` (sigue al mob), `tag`, `ptag` |

Reglas:

- Un hueso **llamado `hitbox` no lleva tag**: se trata como un hueso normal. Renombralo a
  `b_hitbox` (o `ob_hitbox`) para que BetterModel le cree la caja de colision.
- El nombre necesita **al menos dos palabras**: `b` a secas tampoco vale. Escribe `b_body`.
- Las palabras de tag se **consumen**: el hueso `b_body` se llama internamente `body`.

Sin hitbox, el cuerpo vanilla sigue visible a proposito: un mob invisible sin caja de
colision no recibe dano. Este comportamiento se desactiva con
`models.require-hitbox: false` en el config.

> **Los modelos con mesh no sirven.** BetterModel no renderiza geometria de mesh: hay que
> convertirla a cubos en Blockbench antes de exportar, o el modelo sale negro.

### Nombre del mob: `mtag`, `ptag` y `tag` son cosas distintas

| Tag en un hueso | Que pasa con la placa |
|---|---|
| `mtag` (`mtag_head`) | la pinta **el modelo**, pegada al hueso; se oculta si no hay linea de vision |
| `ptag` | la pinta **el modelo**, siempre visible |
| `tag` (`tag_name`) | **el modelo NO pinta placa**: manda el nombre vanilla de la entidad |

En los tres casos el texto es el `customName` del mob, asi que un `display-name` con color
se respeta igual.

**El caso que da problemas es `tag` + `hide-body: true`.** Con el cuerpo oculto y la placa
vanilla, el cliente la vuelve a colocar con cada paquete de la entidad invisible y la placa
**parpadea**. La solucion es renombrar ese hueso a `mtag_...` (o `ptag_...`), que pasa la
placa al modelo, y dejar `show-name` sin tocar.

| `show-name` | Efecto |
|---|---|
| sin la clave (por defecto) | automatico: vanilla apagado si el modelo trae `mtag`/`ptag` |
| `true` | el nombre vanilla siempre visible |
| `false` | nunca el nombre vanilla |

`/customcore model validate` avisa del caso `tag` + `hide-body: true`.

**CustomCore arranca antes que BetterModel** (los plugins se habilitan por orden alfabetico,
y `BetterModel` va despues de `CustomCore`). Durante los primeros segundos los modelos de
BetterModel **aun no estan cargados**. CustomCore lo detecta: los mobs que aparezcan en ese
hueco se completan solos en cuanto BetterModel termina. En el log se ve:

```
[CustomCore] BetterModel enlazado; sus modelos aun se estan cargando. ...
[CustomCore] BetterModel termino de cargar: 14 modelo(s). Se revisan los mobs con modelo.
```

### Animaciones por evento

| Clave | Cuando se reproduce |
|---|---|
| `idle` | en reposo (es el estado inicial del mob) |
| `walk` | al empezar a andar |
| `attack` | al golpear a alguien |
| `hurt` | al recibir dano |
| `death` | al morir, antes de desaparecer |
| `spawn` | al aparecer |

Los valores son **los nombres que las animaciones tienen dentro del modelo**, no los de
esta tabla. Cada entrada admite la forma corta (`idle: reposo`) o la larga:

```yaml
animations:
  walk:
    name: walk        # nombre dentro del modelo
    once: false       # una sola vez, en vez de en bucle (death lo es por defecto)
    speed: 1.2        # velocidad de reproduccion
    start: 0          # ticks de entrada (lerp)
    end: 5            # ticks de salida (lerp)
    priority: 30      # opcional; por defecto la del evento, ver la tabla
```

Un evento sin clave **no anima nada y no se avisa**: es opcional. Un modelo con solo
`attack` es valido, y `hurt` casi nunca existe.

**Reposo y marcha se alternan solos.** El mob arranca en `idle` y pasa a `walk` cuando se
mueve. Si la animacion es `once` (lo normal al exportar de Blockbench), se repite mientras
el mob siga en ese estado: sin eso el modelo se queda congelado al andar.

**Prioridades por defecto:**

| Animacion | Prioridad | Se dispara |
|---|---|---|
| `death` | 100 | el mob muere. Terminal: no vuelve al estado |
| `spawn` | 40 | el mob aparece |
| `attack` | 30 | el mob golpea a alguien |
| `hurt` | 20 | el mob **recibe** dano |
| `walk` | 10 | el mob se mueve |
| `idle` | 0 | el mob esta quieto |

`attack` y `hurt` **no son lo mismo**: uno es el mob pegando y el otro el mob siendo
golpeado.

**El dano gana al golpe**, pero solo si el modelo tiene `hurt`. Si no lo tiene, el golpe
sigue su curso sin cortarse: no se para la animacion para luego no mostrar nada. Lo mismo
vale para cualquier evento: si el yml declara una animacion con un nombre que el `.bbmodel`
no trae, el evento se ignora (y se avisa una vez en el log).

Para invertir el orden, pon `priority` a mano.

**Con `debug: true` en el config** se traza cada cambio de animacion en el log:
`[modelo] reactiva attack (attack) en <uuid>`, `[modelo] estado WALK en <uuid>`,
`[modelo] golpe encadenado: se deja terminar attack`. Sirve para ver el orden real entre
un golpe y la tarea de estado.

**Un golpe encadenado no reinicia la animacion** si aun no ha pasado la mitad: un mob que
pega cada segundo con una animacion de un segundo se veria temblando y nunca completa.

**La duracion se lee del `.bbmodel`.** Al terminar la animacion de un golpe o del dano, el
mob vuelve a su estado; con `death` el tracker se cierra. Nada de tiempos fijos: si tu
animacion dura 1 s, ese es el tiempo que se respeta.

### Asiento de montura (`seat`, solo `category: mount`)

Con `seat: true`, el jugador se sube al hueso marcado como `seat` del modelo: el caballo
vanilla se oculta y se ve solo el modelo montable.

```yaml
category: mount
type: HORSE
model:
  name: caballo_guerra
  seat: true
```

El hueso del asiento debe llamarse **`p_algo`** (tag `p`). No confundir con la hitbox
(`b_`/`ob_`): si no hay hueso `seat`, **no se monta en el modelo** y el log lo dice con la
lista de huesos que encontró.

## Modelos y monturas: por ahora, no

**Un mob `category: mount` NO recibe modelo.** Si su yml trae un bloque `model:`, se
ignora y el log lo avisa una vez.

El motivo, probado en servidor: BetterModel monta al jugador en una **entidad de
interacción** propia y mueve esa entidad, no la montura vanilla. Con el asiento del modelo
el caballo se quedaba quieto y solo se podía girar la vista. Queda pendiente para otra
versión; el código del asiento está escrito por si se retoma.

Consecuencia práctica: una montura se ve como siempre (el caballo vanilla) y se monta a la
manera de Minecraft, que funciona.

**Sin jinete la montura no deambula**: se le apaga la IA mientras nadie la lleva, y se le
vuelve a encender al subirse.

**Los efectos del `mount:`** (`rider`) se aplican al jinete que lleva la montura.

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

**Para depurar por que no aparece ninguno**, pon `debug: true` en el `config.yml` del
plugin. El log dirá entonces, una vez por motivo, que esta fallando:

```
[spawn] 'perro_demoniaco' no aparece cerca de Juan: fuera de la region (allowed='mishkre' …)
[spawn] 'perro_demoniaco' no aparece cerca de Juan: luz 12 fuera de [0,8]
[spawn] 2x 'perro_demoniaco' cerca de Juan en -36500 70 -10500 (world)
```

Mira también que el jugador este de verdad **dentro** de la region: `allowed-region` se
compara con el punto donde va a nacer el mob (entre `distance.min` y `distance.max`
bloques del jugador), no con su posicion.

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
| `village-friendly` | defiende al pueblo: no ataca a aldeanos, golems ni lobos, ni ellos lo apuntan a el, y va a por quien le pegue a un aldeano | `false` |
| `immune` | lista de causas de dano que ignora | vacia |
| `attacks-monsters` | ataca a los monstruos hostiles vanilla (`category: server`) | `false` |
| `transformable` | si el juego puede convertirlo (aldeano -> bruja, zombi -> ahogado...) | `true` |
| `invulnerable` | no recibe **ningun** dano, de ninguna causa (ver abajo) | `false` |

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

### Invulnerable

```yaml
invulnerable: true
```

El mob **no recibe dano de nada**: ni golpes, ni fuego, ni caida, ni pociones, ni el vacio. Por
dentro se traduce en que ignora todas las causas de dano (la misma lista que `immune`), asi que
se comporta igual que poner todas, pero sin escribirlas.

Sirve para un guardian, un mob de mision o un jefe que solo debe caer por su propia mecanica.

> Ojo: sigue siendo un mob normal. Lo que **no** frena es que lo retiren por fuera — `/cm kill`,
> `/cm despedir`, o el hueco de su huevo—, porque eso no pasa por el dano. La invulnerabilidad
> protege de lo que hace dano, no de la administracion.

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
| Un golem (o un muneco de nieve) lo ataca a el | si | **no** |
| Un lobo lo ataca a el | si | **no** |
| Si le pegan a un aldeano cerca | lo ignora | **va a por el agresor** |

El **lobo** merece mencion aparte: caza esqueletos de forma natural, asi que un centinela
amistoso con forma de esqueleto seria su presa. Con la clave, ni el lobo lo apunta ni el mob
ataca de vuelta — pero no se sale en defensa del lobo: el lobo no es del pueblo.

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
- `amount: 0` **desactiva el crafteo**: el mob solo se da con `/customcore give`.
- Sin bloque `recipe`: por defecto huevo + diamante.
- Dos mobs con los mismos ingredientes colisionan: el segundo se omite con un aviso,
  porque el cliente no podria distinguirlos. (`/customcore reload` lo reporta.)

## Skills y drops

- **Skills**: ver `skills.md`.
- **Drops**: ver `drops-y-objetos.md`.
