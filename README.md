# CustomCore

Mobs definidos por YAML, invocados con huevos crafteados, con facciones, disfraces,
equipamiento, encantamientos custom, skills y un cupo de mobs por jugador que sale del
grupo de LuckPerms.

Pensado para **Paper**. Se apoya en tres plugins opcionales: **Teams** (bandos de
jugadores), **LibsDisguises** (apariencias) y **LuckPerms** (grupos y cupos). Sin
cualquiera de ellos el plugin sigue funcionando: cada uno degrada por su cuenta.

## Novedades 0.9.5

- **Sin retorno decreciente**: cada control dura exactamente `amount`. El equilibrio se
  ajusta por skill con `amount` y `cooldown-seconds`; la inmunidad sigue en `config.yml`.
- Correcciones: 0.9.3 arregla el bloqueo que quedaba tras expirar un control; 0.9.4
  bloquea tambien los objetos custom durante aturdimiento o silencio.

## Novedades 0.9.2

- **`stun-free: true`** en el yml de un objeto: se puede usar durante un aturdimiento o
  silencio. Es lo que permite que el trinket rompa el control.

## Novedades 0.9.1

- **Raiz, silencio y desarme** junto al aturdimiento, con el mismo retorno decreciente e
  inmunidad. Cada uno bloquea cosas distintas y se configura en el yml de la skill.

## Novedades 0.9.0

- **Aturdimiento para PvP** (`effect: stun`): no se mueve, no ataca y no usa items.
  Duracion y cooldown en el yml de la skill.
- **Retorno decreciente e inmunidad** configurables en `config.yml`, y **trinket**
  (`effect: cleanse_cc`) que rompe el control y da inmunidad.
- Sin teletransportes ni paquetes: atributos de velocidad y salto a cero, compatible con
  GrimAC.

## Novedades 0.8.0

- **Flechas cargadas**: con una flecha del plugin en la mano, clic derecho la carga en el
  arco. La carga es por jugador, sirve con cualquier arco y sobrevive a reconexiones. El
  disparo gasta una unidad de ese tipo y avisa en el chat si se acaban.

## Novedades 0.7.2

- **Recetas de pociones**: se leian buscando `receta.receta`, asi que quedaban nulas en
  silencio. Ahora se leen bien, se registran al arrancar y se avisa si una receta no se
  puede interpretar.
- Las recetas registradas en caliente se sincronizan con los jugadores conectados, y al
  entrar se les mandan todas las del plugin.

## Novedades 0.7.0

- **Skills al impactar** (`mode: impacto`): flechas, ballesta, municion custom, tridente,
  bola de nieve y huevo ejecutan skills al tocar. `target: impacto` apunta a lo golpeado y
  `target-type: radius` abre el area desde el punto de impacto.
- **Pociones arrojadizas y persistentes**: `material: SPLASH_POTION` o
  `LINGERING_POTION`, con `radio` y `bando`, y su propia receta.

## Novedades 0.6.4

- **`sigilo`** ahora también corta la detección de los mobs: no pueden fijarte como
  objetivo ni mantenerte como objetivo mientras dure.

## Novedades 0.6.3

- **Avisos de skill y de objeto**: `inicio`, `fin` y `cooldown-aviso`, con
  `{segundos}` para la espera restante. Se muestran a quien castea o usa el objeto.

## Novedades 0.6.2

- **Invulnerabilidad** temporal que cancela todo el daño.
- **Atributos temporales** de combate, renovables.
- **`teleport_behind`** con protecciones configurables (por defecto no las consulta).
- **`backstep`**: retroceso mirando al punto de origen.

## Novedades 0.6.1

- **Objetos protegidos**: claves `modificable`, `encantar-libros`, `encantar-mesa`,
  `renombrar`, `reparar`, `desencantar` y `tallar`; se cambian con `item mod`.
- **Una sola clase por jugador**: asignar una clase reemplaza la anterior.
- **Pagos de cadena** con objetos, niveles de XP o dinero.
- **Skills de terreno**: `block_below` y `explosion`.
- **`repair_all`**: repara todo el equipo/inventario del objetivo.

## Novedades 0.6.0

- **Skills reutilizables** en `skills/`: un archivo por skill, referenciables con `ref:`.
- **Skills desde objetos**: `usar.skills` / `usar.skill`, con cooldown maximo entre item y skill.
- **Objetivos por mirada**: `target-type: LOOK|RADIUS` + `target: ENEMIES|ALLIES|SELF`.
  Evita el fuego amigo por diseno; las skills viejas siguen cargando.
- **Fuego y hielo**: `effect: ignite` y `effect: freeze`, con duracion configurable.
- **Curacion porcentual**: `porcentaje: true` en `heal`.
- **Purgas**: `quitar_positivos` / `quitar_negativos`, del plugin y de Minecraft.
- **Permiso `customcore.bypass`** separado de `customcore.admin`.
- **`/customcore testcast <skill>`** para probar distancias sin depender del clic (admin).

---

## Requisitos

- **Paper** 1.21 o superior (probado en Paper 26.3).
- **Java 21+**. Ojo: Paper 26.3 exige **Java 25** en el servidor.
- Opcionales, cada uno independiente:
  - **LuckPerms** — para el cupo por grupo. Sin el, todos caen al cupo por defecto.
  - **Teams** — para bandos de jugadores. Sin el, cada jugador solo se protege a si mismo.
  - **LibsDisguises** — para dar apariencia a los mobs.

---

## Instalacion

1. Copia el jar a `plugins/`.
2. Arranca el servidor una vez. Se crean:
   - `plugins/CustomCore/config.yml` — configuracion general.
   - `plugins/CustomCore/factions.yml` — relaciones entre facciones.
   - `plugins/CustomCore/mobs/` — un archivo por mob. Se copian **seis ejemplos
     numerados** que funcionan como tutorial: abrelos en orden.
   - `plugins/CustomCore/skills/` — skills reutilizables, una por archivo.
3. Edita lo que quieras.
4. `/customcore reload` — recarga mobs, `skills/`, items, config y facciones **sin reiniciar**.

> `plugin.yml` es la excepcion: los permisos que declara solo se leen al arrancar. Si
> cambias sus valores por defecto hace falta reiniciar el servidor.

---

## Primeros pasos: tu primer mob

Crea `plugins/CustomCore/mobs/guardia.yml`:

```yaml
id: guardia
category: player
display-name: '&bGuardia'
type: ZOMBIE
egg: ZOMBIE_SPAWN_EGG

attributes:
  health: 40.0
  damage: 5.0

skills:
  - id: aviso
    effect: message
    target: self
    range: 20.0
    cooldown-seconds: 30
    message: '{mob} &fVigilando la zona.'
```

`/customcore reload` y luego, dentro del juego:

- `/customcore give guardia` — te da el huevo.
- O craftealo: **huevo de zombi + diamante** (la receta por defecto).
- Usa el huevo → aparece el Guardia, y **tu eres su dueno**.

---

## El archivo de un mob

Todo mob vive en `mobs/<archivo>.yml`. Solo `id` y `type` son obligatorios.

### Identidad

| Campo | Valores | Por defecto | Notas |
|---|---|---|---|
| `id` | texto | nombre del archivo | identificador unico |
| `category` | `player` \| `server` | `player` | ver abajo |
| `display-name` | texto con `&` | el `id` | los codigos `&` se traducen a color |
| `type` | tipo de entidad | — | `ZOMBIE`, `SKELETON`, `VILLAGER`, `RAVAGER`… cualquier entidad que sea un `Mob` |
| `glow` | `true` \| `false` | `false` | contorno visible |
| `lore` | lista de textos | vacio | se muestra en el huevo |

**`player`** — se invoca con un huevo crafteado, tiene dueno y lo protege.
**`server`** — no tiene dueno, pertenece a una faccion, y puede ser un spawner.

### Atributos

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

### Equipamiento

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

### Encantamientos

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

### Objetivos

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

### Ancla y leash

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

Un mob de jugador con `anchor: point` mantiene **cargada** una zona a su alrededor, para
no desaparecer ni dejar de contar cuando no hay nadie cerca. El radio se configura por
mob:

```yaml
chunk-radius: 4   # chunks a la redonda; sin esto, usa point.chunk-radius del config
```

Ojo: son `(2*radio+1)^2` chunks. Con 4, **81 chunks por mob**.

### Apariencia, sonidos y resto

```yaml
disguise:
  enabled: true
  type: player
  skin: NombreDeJugador
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

- `burn-in-daylight`: por defecto `false` en mobs de servidor y `true` en los de jugador.
- `ai: false`: el mob se queda quieto en su puesto. Util para figurantes.
- `respawn-seconds`: solo mobs de servidor. `0` = una sola vida. Con un valor mayor,
  el punto funciona como **spawner** de ese mob.

### Crafteo

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

### Skills

```yaml
skills:
  - id: golpe_pesado
    effect: damage
    target: target
    range: 4.0
    amount: 9.0
    cooldown-seconds: 8
    chance: 0.6

  - id: grito
    effect: message
    target: self
    range: 20.0
    cooldown-seconds: 60
    message: '{mob} &fAlto ahi.'

  - id: vendaje
    effect: heal
    target: owner
    range: 12.0
    amount: 6.0
    cooldown-seconds: 25
    message: '&aTe venda las heridas.'

  - id: veneno
    effect: potion
    target: enemies
    range: 8.0
    potion: poison
    potion-duration-seconds: 6
    potion-amplifier: 0
```

| Campo | Valores | Por defecto |
|---|---|---|
| `id` | texto unico dentro del mob | — |
| `effect` | `damage` \| `heal` \| `message` \| `potion` | — |
| `target` | `self` \| `owner` \| `target` \| `enemies` \| `allies` | — |
| `mode` | `pasiva` \| `activa` \| `interaccion` | `pasiva` |
| `range` | bloques (tope 32) | `8.0` |
| `cooldown-seconds` | entero | `5` |
| `chance` | `0.0` a `1.0` | `1.0` |
| `amount` | dano o curacion | `0.0` |
| `message` | texto con `&` | — |
| `potion` | `poison`, `speed`, `weakness`… | — |

### Cuando se dispara una skill (`mode`)

| `mode` | Cuando entra |
|---|---|
| `pasiva` | **siempre**, mientras el mob este cargado (valor por defecto) |
| `activa` | solo **en combate**, cuando el mob tiene a alguien a quien atacar |
| `interaccion` | cuando un jugador hace **clic derecho** sobre el mob |

```yaml
skills:
  # Dialogo: se dispara con clic derecho. El texto va solo a quien hizo clic,
  # y admite {jugador} ademas de {mob}.
  - id: saludo
    mode: interaccion
    effect: message
    target: self
    cooldown-seconds: 3
    message: '&e{mob}&f: Buenas, {jugador}.'
```

Si el jugador trae un huevo custom en la mano, ese clic es del huevo: la interaccion no
se dispara.

Detalles que importan:

- Los objetivos usan **la misma politica de combate que la IA**: si el mob considera
  enemigo a alguien, sus skills tambien. No hay dos definiciones de "enemigo".
- `enemies` incluye a quien agredio a su bando (venganza), no solo a los enemigos
  declarados.
- En `effect: message` el `target` **se ignora**: el mob habla a los jugadores dentro
  de `range`. `{mob}` se sustituye por su nombre.
- `message` tambien funciona en `damage`, `heal` y `potion`: si el objetivo es un
  jugador, le llega el aviso. Es la forma de que una skill sea **observable**.
- Si falla la tirada de `chance`, se reintenta en el siguiente ciclo **sin gastar** el
  cooldown.
- Una skill mal escrita se descarta sola. Nunca tumba la carga del mob.
- El intervalo de evaluacion es `skills.interval-ticks` del config (20 = 1 segundo).

### Tabla de drops

```yaml
drops:
  # Objetos vanilla: legibles y a mano.
  - material: DIAMOND
    amount: 2
    chance: 0.5

  # Objetos con NBT: se referencian por NOMBRE (ver el catalogo mas abajo).
  - item: espada_del_sargento
    chance: 0.25
    min: 1
    max: 1

# true = el mob no suelta NADA de lo vanilla: solo su tabla.
clear-vanilla-drops: false
```

`amount` es un atajo de `min`/`max`. Un nombre que no exista en el catalogo no rompe
nada: se avisa **una vez** en el log, nombrando el mob, y ese drop se omite.

---

## Catalogo de objetos (NBT)

Hay objetos que **no se pueden escribir a mano** en un yml: un encantamiento de
ExcellentEnchants, cualquier NBT puesto por otro plugin. Para esos esta el catalogo.

```
/customcore item save <nombre>     guarda el objeto de tu mano
/customcore item list              lista los nombres guardados
/customcore item remove <nombre>   borra uno
```

Cada objeto vive en **`items/<nombre>.yml`**, con su nombre escrito dentro:

```yaml
nombre: espada_del_sargento
item: '<base64 con todo el NBT>'
etiqueta: '&cEspada del Sargento'
```

El nombre se normaliza a minusculas y sin espacios, porque acaba siendo un nombre de
archivo. Despues lo llamas por ese nombre desde la tabla de drops o desde el
equipamiento. **Un mismo objeto sirve para varios mobs.**

---

## Color y brillo de los player mobs

Cada jugador puede distinguir sus mobs de los de los demas:

```
/customcore color <color|nada>     color del nombre
/customcore glow <color|nada>      brillo, y de que color
```

- El color **tine el nombre entero** y sobreescribe el que escribio el admin en el yml.
- El brillo **se hereda del yml** del mob. Si el yml no lo tiene, se queda sin glow.
  `nada` lo apaga explicitamente.
- **Con Teams, el ajuste es del team**: lo decide solo su jefe y aplica a los mobs de
  todos los miembros, para que un equipo se vea uniforme. Sin Teams, cada jugador manda
  sobre los suyos.

El color del brillo lo determina un **equipo de scoreboard** (asi funciona en Minecraft),
no la entidad: el plugin crea equipos `cm_<color>` y limpia la entrada al morir o al
cambiar de color.

Se guarda en `styles.yml` y se aplica en caliente: cambias el color y tus mobs vivos se
actualizan al momento.

---

## Facciones y actitud

Solo para `category: server`. Las relaciones se declaran en `factions.yml`:

```yaml
orcos:
  allies: [goblins]
  enemies: [humanos, enanos]
```

Las relaciones son **simetricas**: si `orcos` declara `humanos` enemigo, la enemistad
vale al reves. Lo que no se declara es **neutral**, y dos facciones neutrales solo se
atacan si una agrede a la otra.

La actitud frente a **jugadores** se define por mob:

| `attitude` | Comportamiento |
|---|---|
| `hostile` | ataca jugadores a la vista |
| `neutral` | no ataca salvo que lo agredan |
| `defender` | nunca ataca jugadores; persigue a los monstruos que los amenazan |

---

## Comandos

Todos bajo `/customcore` (alias `cmobs`, `cm`).

| Comando | Que hace |
|---|---|
| `reload` | recarga mobs, config y facciones sin reiniciar |
| `list` | lista las definiciones cargadas |
| `give <id> [jugador]` | entrega el huevo de ese mob |
| `spawn <id> [mundo x y z]` | invoca el mob. Desde el juego, tu quedas como dueno |
| `kill <id>` | mata todos los mobs de esa definicion |
| `remove [radio]` | retira los mobs custom cercanos |
| `active` | lista los mobs vivos, con dueno y ubicacion |
| `enchants [filtro]` | lista encantamientos disponibles (util para validar ids) |
| `cuota [jugador]` | consulta el cupo de mobs |
| `cuota recontar <jugador>` | rehace la cuenta **sin tocar mobs** |
| `cuota retirar <jugador>` | retira sus mobs y deja el cupo en cero (**destructivo**) |
| `item save <nombre>` | guarda el objeto de tu mano en el catalogo, con su NBT |
| `item list` | lista los objetos guardados |
| `item remove <nombre>` | borra uno del catalogo |
| `color <color\|nada>` | color del nombre de tus player mobs |
| `glow <color\|nada>` | brillo de tus player mobs |
| `spawner list` | lista los spawners (id, mob, mundo, coordenadas) |
| `spawner remove <id>` | borra un spawner y su mob |
| `spawner removeall [mob]` | borra todos los spawners, o los de un mob |
| `spawner reload` | relee `data/spawners.yml` sin reiniciar |
| `book [jugador]` | entrega el libro de inspeccion (alias `libro`) |
| `upgrade list` | lista los items de mejora cargados |
| `upgrade give <id> [jugador]` | reparte un item de mejora (alias `mejora`) |
| `upgrade reload` | relee `upgrades/` y rehace las recetas |

**`recontar` vs `retirar`** — la diferencia es importante:

- `recontar`: no mata nada. Deja en la cuenta solo los mobs que se ven ahora. Una
  entrada de un mob que ya no existe desaparece y no vuelve; la de un mob real que
  esta en un chunk descargado **volvera a contar** cuando su chunk cargue. Sirve para
  corregir cuentas infladas.
- `retirar`: **mata** los mobs que alcanza y deja el cupo en cero. Los que estan
  anclados en chunks descargados no se pueden tocar hasta que carguen.

---

## Permisos y cupos

| Permiso | Para que | Por defecto |
|---|---|---|
| `custommobs.player` | invocar mobs con los huevos | `true` |
| `custommobs.admin` | comandos de administracion | `op` |

Un jugador raso puede hacer **tres cosas** por su cuenta, y nada mas: `cuota` sin
argumentos (su cupo), y `color` / `glow` (el estilo de **sus** mobs; con Teams lo decide
solo el jefe y aplica a todo el team). Todo lo demas exige `custommobs.admin`.

> **Ojo con `default: false`**: a diferencia de `op`, **ignora** el flag de operador.
> Si lo cambias, ni tu (siendo op) podreis invocar hasta que se os conceda el permiso.

Los permisos van por el sistema **nativo de Bukkit**, asi que sirve cualquier plugin de
permisos, no solo LuckPerms.

### Cupo de mobs por grupo

En `config.yml`:

```yaml
limits:
  default-player-mobs: 3
  groups:
    default: 3
    vip: 5
    staff: 10
```

El cupo esta **partido en dos cuentas que no se pisan**: los mobs que te siguen
(`anchor: owner`) y los fijos a un bloque (`anchor: point`). Puedes llevar una llena y
seguir colocando de la otra. `/customcore cuota` muestra las dos.

- El grupo se lee de **LuckPerms en el momento de invocar**, no al conectarse: un cambio
  de rango aplica al instante.
- Si el jugador pertenece a **varios grupos, gana el cupo mas alto**.
- Si su grupo no esta mapeado, o no se pudieron leer sus grupos, se usa
  `default-player-mobs`. `0` = sin limite.
- Sin LuckPerms instalado, todos caen al grupo `default`.
- Cambiar `limits` aplica con `/customcore reload`, **sin reiniciar**.

El cupo se lleva en `player-mobs.yml` y **cuenta los mobs anclados a un bloque aunque
su chunk este descargado** — un conteo que solo mirase las entidades cargadas se
saltaria el tope desplegando mobs por bloques repartidos.

---

## Libro de inspeccion e items de mejora

**El libro** muestra al jugador sus mobs con sus estadisticas de combate. Lo reparte solo
un administrador (`/customcore book`), pero **cualquiera puede usarlo**: asi el servidor lo
puede vender o regalar desde su economia. Al usarlo se abre un libro con una pagina por mob
(vida actual y maxima, ataque, velocidad, armadura y ubicacion) y tambien aparecen los mobs
que el jugador tiene guardados en su huevo.

**Los items de mejora** suben caracteristicas de combate. Se definen en `upgrades/`, uno por
archivo:

```yaml
id: piedra_vida
material: AMETHYST_SHARD
display-name: '&dPiedra de vida'
lore:
  - '&7Se la das a un mob tuyo'
stats:
  health: 20.0          # cuanto sube, no el valor final
recipe:
  amount: 0             # 0 = solo por comando; 1+ = crafteable (sin forma)
  extras: [DIAMOND, DIAMOND]
```

Solo se admiten caracteristicas de combate: `health`, `damage`, `speed`, `armor`,
`armor-toughness`, `knockback-resistance`, `attack-speed` y `attack-knockback`.

El jugador lo sostiene y hace **clic derecho sobre un mob suyo**: la mejora se aplica y el
item se consume. Como el huevo guarda las desviaciones de atributos, **la mejora viaja con
el mob** cuando se le recoge y se le vuelve a colocar.

## Configuracion general (`config.yml`)

```yaml
debug: false

targeting:
  radius: 16.0                 # radio de busqueda de objetivos
  interval-ticks: 20           # cada cuanto se revisan
  player-mode: defensive       # never | enemies | non-allies | defensive
  aggro-duration-seconds: 120  # cuanto dura la venganza
  aggro-watch-radius: 24.0     # radio en el que se percibe la agresion al bando
  aggro-on-friendly-mobs: true # reacciona si agreden a un mob del mismo bando

skills:
  interval-ticks: 20           # cada cuanto se evaluan las skills

permissions:
  player: custommobs.player
  admin: custommobs.admin

limits:
  default-player-mobs: 3          # mobs que te siguen
  default-point-mobs: 1           # mobs fijos a un bloque
  groups: { default: 3, vip: 5, staff: 10 }
  point-groups: { default: 1, vip: 2, staff: 3 }

point:
  chunk-radius: 4                 # chunks que carga un mob fijo a su alrededor

branding:
  motd-enabled: true                     # false = sin mensaje de bienvenida
  motd:                                  # se manda al entrar; admite '&' para color
    - '&0&m----------------------------------'
    - ' &cCustomCore &0| &cAP2P Project'
    - ' &cBienvenido, &4{jugador}&c.'
    - '&0&m----------------------------------'

worlds:
  enabled: []                            # mundos donde funciona; vacio = todos

recall:
  enabled: true                          # ventana de abandono
  radius: 0.0                            # 0 = derivado de la simulation-distance
  mob-seconds: 60                        # el mob abandonado se destruye a los 60 s
  chunk-seconds: 120                     # su chunk se libera a los 120 s
```

El MOTD admite los marcadores `{jugador}`, `{online}` y `{max}`. Cambiarlo **no pide
reinicio**: se aplica con `/customcore reload`.

La **firma** (`Plugin by: AP2P Project`) no se configura: va hardcodeada en el codigo,
con los colores de la bandera palestina.

Ojo con donde se ve ese color: la firma se pinta en la **consola del servidor** (la
terminal, o el panel que uses), porque los colores viajan como codigos ANSI. En el
**archivo** `logs/latest.log` sale en texto plano: Paper le quita el formato a todo lo que
escribe un plugin. Es como funcionan los logs, no una limitacion evitable.

### Venganza (aggro)

Cuando un jugador agrede al **bando protegido** de un mob —su dueno, un companero, un
aliado o un mob del mismo lado— los mobs de ese bando en `aggro-watch-radius` lo marcan
como hostil durante `aggro-duration-seconds`, **sin importar el modo de objetivos**.
La IA y las skills reaccionan igual, porque consultan el mismo registro.

### Ventana de abandono

Un mob anclado a su dueno **solo lo sigue a un mundo habilitado**. Si te vas a un mundo
excluido, o lo dejas demasiado lejos, el mob no puede volver a tu lado y entra la
**ventana de abandono**: se pide un ticket de chunk en su posicion, te avisa por chat, el
mob **se destruye a los 60 s** (y su huevo queda inerte) y su chunk **se libera a los
120 s**. Si vuelves antes, la cuenta se cancela y el mob se conserva.

**Estar desconectado no es abandono**, y los mobs anclados a un punto no siguen a nadie:
nunca se abandonan.

Los mundos fuera de `worlds.enabled` bloquean tambien la invocacion: alli el huevo no
hace nada, con aviso.

---

## API para otros plugins

Se registra un servicio `CustomCoreApi` en el `ServicesManager`:

```java
CustomCoreApi api = getServer().getServicesManager().load(CustomCoreApi.class);

api.definitionIds();                              // ids de mobs cargados
api.isCustomMob(entity);                          // es un mob gestionado?
api.viewOf(entity);                               // vista de solo lectura
api.spawn("guardia", location, ownerPlayer);      // invoca uno
```

---

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
| `upgrades/*.yml` | un archivo por item de mejora |

El vinculo de cada mob (dueno, team, definicion, punto de aparicion) vive en el
**PersistentDataContainer** de la entidad, asi que sobrevive reinicios sin archivo
aparte. Los spawners son la excepcion: su punto debe sobrevivir a la muerte del mob.

---

## Resolucion de problemas

**Un mob no aparece al usar el huevo.**
Revisa en este orden: (1) que tengas `custommobs.player`; (2) que no hayas llegado a tu
cupo — `/customcore cuota`; (3) que el mob tenga `egg` y que su crafteo no este apagado
(`recipe.amount: 0`).

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

---

## Compilar y publicar una version

Para compilar hace falta **JDK 25**: `paper-api` 26.3 viene en bytecode de Java 25 y un
javac anterior no puede leerlo. La salida, en cambio, es **bytecode 21**, o sea que el jar
funciona en servidores con **Java 21+**.

```
mvn -B clean package        # deja Core/target/CustomCore-<version>.jar
```

Para publicar una version nueva:

1. Sube el numero en los **tres** `pom.xml` (el `plugin.yml` lo hereda por filtrado).
2. Anota los cambios en `CHANGELOG.md`.
3. `git commit`, `git tag -a vX.Y.Z -m '...'` y `git push origin master vX.Y.Z`.
4. Adjunta el jar:

```
gh release create vX.Y.Z Core/target/CustomCore-X.Y.Z.jar \
  --title 'CustomCore X.Y.Z' --notes-file CHANGELOG.md
```

### Por que no hay build automatico (todavia)

El proyecto compila contra tres APIs de plugins **opcionales** que hoy no estan en ningun
repositorio publico accesible:

- `com.hites.godxteam:teams-api` (bandos de jugadores)
- `me.libraryaddict:libsdisguises` (apariencias) — la version publica usa otras
  coordenadas y otro repositorio
- `com.github.retrooper:packetevents-spigot` — lo exige la API de LibsDisguises al compilar

Mientras sigan asi, un runner limpio no puede resolverlas y el jar hay que adjuntarlo a
mano. La solucion de fondo es dejar de compilar contra ellas y llamarlas por reflexion,
como ya se hace para cargarlas en tiempo de ejecucion.

## Licencia

GNU General Public License v3.0. Mira `LICENSE`.

Es software libre: puedes usarlo, estudiarlo, modificarlo y redistribuirlo. Si
redistribuyes una version modificada, tiene que seguir siendo libre. Nada de cerrarlo y
venderlo como caja negra.
