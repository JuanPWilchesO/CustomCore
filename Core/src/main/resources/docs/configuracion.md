# Configuracion general (`config.yml`)

```yaml
debug: false

branding:
  motd-enabled: true
  motd:
    - '&0&m----------------------------------'
    - ' &cCustomMobs &0| &cAP2P Project'
    - ' &cBienvenido, &4{jugador}&c.'
    - '&0&m----------------------------------'

targeting:
  radius: 16.0
  interval-ticks: 20
  player-mode: defensive
  aggro-duration-seconds: 120
  aggro-watch-radius: 24.0
  aggro-on-friendly-mobs: true

skills:
  interval-ticks: 20

permissions:
  player: custommobs.player
  admin: custommobs.admin

limits:
  default-player-mobs: 3
  default-point-mobs: 1
  groups:
    default: 3
    vip: 5
    staff: 10
  point-groups:
    default: 1
    vip: 2
    staff: 3
  default-mount-mobs: 1
  mount-groups:
    default: 1
    vip: 2
    staff: 3

point:
  chunk-radius: 4

worlds:
  enabled: []

recall:
  enabled: true
  radius: 0.0
  mob-seconds: 60
  chunk-seconds: 120

spawning:
  enabled: true
  interval-seconds: 10
  attempts: 2
  global-cap: 80

book:
  material: WRITTEN_BOOK
  display-name: '&6Inventario de fuerzas'
  lore:
    - '&7Muestra tus mobs y sus estadisticas.'
    - '&7Clic derecho para abrirlo.'
```

## Seccion por seccion

| Seccion | Que controla |
|---|---|
| `debug` | log extra |
| `branding.motd-enabled` | si el mensaje de bienvenida se manda |
| `branding.motd` | las lineas del MOTD (admite `&` para color) |
| `targeting.radius` | radio de busqueda de objetivos |
| `targeting.interval-ticks` | cada cuanto se revisan los objetivos |
| `targeting.player-mode` | politica por defecto contra jugadores |
| `targeting.aggro-duration-seconds` | cuanto dura la venganza |
| `targeting.aggro-watch-radius` | radio en el que se percibe la agresion al bando |
| `targeting.aggro-on-friendly-mobs` | reacciona si agreden a un mob del mismo bando |
| `skills.interval-ticks` | cada cuanto se evaluan las skills |
| `permissions.player` | permiso para usar los huevos |
| `permissions.admin` | permiso para los comandos de admin |
| `limits.default-player-mobs` | cupo de mobs anclados al dueno, sin grupo mapeado |
| `limits.default-point-mobs` | cupo de mobs anclados a un bloque, sin grupo mapeado |
| `limits.groups` | cupo por grupo de LuckPerms (anclados al dueno) |
| `limits.point-groups` | cupo por grupo para los anclados a un bloque |
| `limits.default-mount-mobs` | cupo de monturas, sin grupo mapeado |
| `limits.mount-groups` | cupo de monturas por grupo de LuckPerms |
| `point.chunk-radius` | radio en chunks que se carga alrededor de un bloque ancla |
| `worlds.enabled` | mundos donde funciona el plugin; vacio = todos |
| `recall.enabled` | si los mobs abandonados se retiran solos |
| `recall.radius` | radio de abandono; `0` = derivado de la simulation-distance |
| `recall.mob-seconds` | segundos antes de destruir el mob abandonado |
| `recall.chunk-seconds` | segundos antes de liberar el chunk que se mantuvo cargado |
| `spawning.enabled` | si los mobs de servidor brotan solos por el mundo |
| `spawning.interval-seconds` | cada cuanto se intenta |
| `spawning.attempts` | intentos por jugador y ciclo |
| `spawning.global-cap` | tope de mobs naturales vivos a la vez |
| `book.material` | material del libro de inspeccion |
| `book.display-name` | nombre del libro |
| `book.lore` | descripcion del libro |

## Tres cuentas de mobs

El cupo de mobs de jugador esta **partido en tres**, y no se pisan:

- **Anclados al dueno** (`anchor: owner`): los que te siguen. Los manda
  `limits.default-player-mobs` y `limits.groups`.
- **Anclados a un bloque** (`anchor: point`): los fijos. Los manda
  `limits.default-point-mobs` y `limits.point-groups`.
- **Monturas** (`category: mount`): las que montas. Las manda
  `limits.default-mount-mobs` y `limits.mount-groups`.

Asi puedes llevar el cupo de seguidores y el de fijos llenos y, ademas, tener tu montura.
Un `0` en cualquiera de las tres significa **sin limite** para esa cuenta.

## Zona cargada de un mob fijo (`point.chunk-radius`)

Un mob de jugador anclado a un bloque mantiene cargada una zona a su alrededor para no
desaparecer ni dejar de contar aunque no haya jugadores cerca. El radio se mide en chunks
y el mob puede sobrescribirlo con su propio `chunk-radius`.

**Coste**: es un cuadrado de `(2*radio+1)^2` chunks por mob. Con el valor por defecto (4)
son **81 chunks**. Bajalo o ponlo en `0` si notas presion de memoria.

## El MOTD

Admite los marcadores `{jugador}`, `{online}` y `{max}`. Cambiarlo **no pide reinicio**:
se aplica con `/custommobs reload`.

La **firma** (`Plugin by AP2P Project`) no se configura: va hardcodeada en el codigo, con
los colores de la bandera palestina.

Ojo con donde se ve ese color: la firma se pinta en la **consola del servidor** (la terminal,
o el panel que uses), porque los colores viajan como codigos ANSI. En el **archivo**
`logs/latest.log` sale en texto plano: Paper le quita el formato a todo lo que escribe un
plugin. Es como funcionan los logs, no una limitacion evitable.

## Ventana de abandono (`recall`)

Cuando dejas atras un mob anclado a ti —estas en otro mundo no habilitado, o demasiado
lejos— el mob recibe un **plazo** antes de retirarlo:

1. Se pide un **ticket de chunk** en su posicion y te llega un **aviso por chat**. El
ticket es imprescindible: sin el, la chunk se descargaria y no habria forma de destruir
al mob cuando venza el plazo.
2. A los `mob-seconds` el mob se **destruye** y su vinculo se borra, asi que su huevo
queda inerte.
3. A los `chunk-seconds` se **suelta el ticket** y la chunk vuelve a lo normal.

- Si el dueno **vuelve antes** del plazo, la cuenta se cancela y el mob se conserva.
- **Estar desconectado no es abandono**: al volver, tus mobs siguen donde estaban.
- Los mobs **anclados a un punto** no siguen a nadie, asi que no se abandonan.
- `chunk-seconds` nunca puede ser menor que `mob-seconds`: soltar la chunk antes de
destruir el mob lo dejaria fuera de juego, sin forma de tocarlo.
- `radius: 0.0` deriva el radio de la `simulation-distance` del mundo. Pon un valor fijo
si quieres otro comportamiento.

## Aparicion aleatoria (`spawning`)

Los mobs de `category: server` pueden **brotar solos** por el mundo si su yml lo pide con
`spawn.natural`. Es **aparte de los spawners**: un spawner es un punto fijo que reaparece;
esto son mobs al azar, de **una sola vida** y sin dejar spawner. No los toca ni los
sustituye: solo suma.

```yaml
spawning:
  enabled: true
  interval-seconds: 10
  attempts: 2
  global-cap: 80
```

| Clave | Que hace |
|---|---|
| `enabled` | si la aparicion aleatoria funciona |
| `interval-seconds` | cada cuanto se intenta |
| `attempts` | intentos por jugador y ciclo |
| `global-cap` | tope de mobs naturales vivos a la vez en todo el servidor |

En el mob se pide con su propia seccion `spawn:` (probabilidad, tamano de grupo, hora, luz,
altura, distancia al jugador y tope propio). Ver `el-archivo-de-un-mob.md`.

## Brillo de facciones (`faction-glow`)

Pinta el brillo de cada jugador segun su faccion, **para cada quien**: enemigos en rojo,
aliados en verde, sin relacion o sin faccion en gris.

```yaml
faction-glow:
  mode: off        # off | global | player
  radius: 48.0     # 0 = sin limite
  enemy: RED
  ally: GREEN
  neutral: GRAY
```

| `mode` | Que pasa |
|---|---|
| `off` | nadie ve el brillo |
| `global` | lo ven todos, siempre |
| `player` | cada jugador lo decide con `/custommobs glowfaccion` |

El color de un brillo sale del equipo de scoreboard, y los equipos son **globales**: pintarlo
de una vez no serviria, porque la relacion es distinta para cada espectador. Por eso se le
manda a cada jugador un **equipo falso** (con PacketEvents) y **no se toca el scoreboard de
verdad** — asi no se rompe Teams ni ningun otro plugin.

**Necesita PacketEvents.** Sin el, el brillo de facciones no funciona.

## Coherencia con Teams (`teams`)

```yaml
teams:
  block-join-if-owner-factionless: false
```

Las reglas de faccion y los teams tienen que cuadrar. En vez de **sacar** a nadie cuando algo
no cuadra, el plugin **impide** que la incoherencia llegue a producirse: se rechaza la union.

Se rechaza **en los dos sentidos**:

- Si es **miembro** y su faccion no cuadra con la del jefe del team, no se le deja entrar.
- Si es **el jefe** y alguno de los suyos es de otra faccion, tampoco se le deja entrar.

`block-join-if-owner-factionless` decide si se **deja entrar** a alguien cuando el jefe del team
**no tiene** faccion.

Y al **conectarse** (`PlayerJoinEvent`) se repasa el team: si quedo incoherente (por ejemplo,
alguien que estaba desconectado cuando cambio su faccion), **se avisa en el log**. Ya no se
expulsa a nadie — no hace falta, porque la entrada ya se bloquea.

No se forkea Teams: sus eventos cubren todo. Lo unico imposible por su API sigue siendo **sacar**
a alguien, y ya no hace falta.

## Filtrar mundos (`worlds.enabled`)

```yaml
worlds:
  enabled:
    - world
    - mundo_pvp
```

- Lista **vacia** = el plugin funciona en todos los mundos.
- Fuera de la lista, **el plugin no hace nada**: el mob no se gestiona —no cuenta cupo ni
  recibe IA—, el huevo no invoca nada, no se craftean huevos ni items de mejora, y ni el libro
  ni los objetos reservados a una faccion actuan. Ese mundo queda como si el plugin no estuviera.

Tres funciones pueden **desligarse** de esa lista, cada una por su cuenta:

```yaml
worlds:
  enabled:
    - world
  anywhere:
    item-faction-lock: false
    player-friendly-fire: false
    faction-glow: false
```

En `true`, la funcion vale en **todos** los mundos, aunque `enabled` la excluya; en `false`,
sigue la lista. Sirve para tener, por ejemplo, PvP con facciones en un mundo y sin ellas en otro.
- Los nombres son los de Bukkit, asi que vale cualquiera, incluidos los de Multiverse-Core
  (mira el nombre exacto con `/mv list`).
