# Facciones, equipos y combate

## A quien ataca un mob

Depende de su categoria:

- **Mob de jugador** (`category: player`): nunca ataca a su dueno ni a si mismo. Ataca a
  los monstruos vanilla, a los mobs custom cuyo team sea enemigo del suyo, y a jugadores
  solo segun `targets.players` (o el modo del config). **Sin team, nunca ataca jugadores.**
- **Mob de servidor** (`category: server`): ataca solo a mobs de facciones enemigas. Los
  de su bando o aliadas son intocables. Contra jugadores manda `attitude` (`hostile`,
  `neutral`, `defender`).

## Proteccion

Un mob defiende al dueno, a los companeros de team, a los aliados y a los mobs de su
bando; sin team, solo a su dueno. **La defensa propia siempre vale.** Los aliados son
intocables aunque hayan agredido antes.

## Facciones (`factions.yml`)

Solo para mobs `category: server`:

```yaml
orcos:
  allies: [goblins]
  enemies: [humanos, enanos]
```

- Las relaciones son **simetricas**: si `orcos` declara `humanos` enemigo, la enemistad
  vale al reves.
- Lo que no se declara es **neutral**. Dos facciones neutrales solo se atacan si una
  agrede a la otra.

## Actitud frente a jugadores

| `attitude` | Comportamiento |
|---|---|
| `hostile` | ataca jugadores a la vista |
| `neutral` | no ataca salvo que lo agredan |
| `defender` | nunca ataca jugadores; persigue a los monstruos que los amenazan |

## Modos contra jugadores (mobs de jugador)

```yaml
targets:
  players: defensive
```

| Modo | Contra jugadores |
|---|---|
| `never` | nunca |
| `enemies` | solo si Teams los marca enemigos |
| `non-allies` | todo jugador que no sea de su team ni aliado |
| `defensive` | neutral hasta que agredan a su bando |

Sin el campo, se hereda `targeting.player-mode` del config.

## Venganza (aggro)

Cuando un jugador agrede al **bando protegido** de un mob —su dueno, un companero, un
aliado o un mob del mismo lado— los mobs de ese bando dentro de `aggro-watch-radius` lo
marcan como hostil durante `aggro-duration-seconds`, **sin importar el modo de
objetivos**. La IA y las skills reaccionan igual, porque consultan el mismo registro.

Config:

```yaml
targeting:
  aggro-duration-seconds: 120
  aggro-watch-radius: 24.0
  aggro-on-friendly-mobs: true
```

## Fuego amigo

Un mob **no se dana con su propio bando**:

- Pegarle con la espada a **tu propio mob** no le quita vida.
- Un **companero de team** o un **aliado** tampoco le hace dano.

Asi puedes pelear **al lado** de tus mobs sin herirlos. Es la misma regla de "aliado
intocable" que usa la IA para elegir a quien atacar.

## Facciones de jugador

Un administrador puede meter a un jugador en una faccion:

```
/custommobs faction set <jugador> <faccion>
```

A partir de ahi, los mobs de `category: server` lo tratan por `factions.yml`, no por su
actitud:

| Relacion con la faccion del mob | Efecto |
|---|---|
| la **misma** faccion | intocable |
| una faccion **aliada** | intocable |
| una faccion **enemiga** | objetivo |
| **neutral** | manda la `attitude` del mob, como siempre |

Sin faccion asignada no cambia nada: manda la actitud.

El **fuego amigo tambien se bloquea**: un jugador de la misma faccion que un mob —o de una
faccion aliada— no le hace dano, ni el a el ni sus mobs al jugador, y eso vale aunque haya
agredido antes.

**Un jugador tiene una sola faccion.** Asignarle otra **reemplaza** la anterior; con
`faction clear` se queda sin ninguna. No hay mas restriccion que el nombre: si no coincide con
ninguna faccion de `factions.yml` ni de ningun mob, el plugin lo avisa por si es una errata.

Se guarda en `data/player-factions.yml`, un fichero del propio plugin: **no hace falta
LuckPerms**. `faction get`, `faction clear` y `faction list` completan el manejo, y todo ello
es de administracion.

## El mob de un jugador es una extension suya

Un mob de `category: player` no vive en su propio bando: **hereda el de su dueno**. Para el
sistema de facciones vale la faccion de quien lo lleva, y eso decide a quien ataca y de quien
se defiende:

- **Ataca a los enemigos de su dueno**: jugadores de una faccion enemiga, y los mobs de esos
  jugadores.
- **No toca a los de su faccion ni a los aliados** — y tampoco ellos lo tocan a el: el fuego
  amigo esta bloqueado por faccion, no solo por team.
- **Los mobs de faccion enemiga van a por el**, igual que irian a por su dueno. Un soldado tuyo
  es, para un orco hostil, como si fueras tu.
- Sin faccion asignada a nadie, manda la actitud y el team de siempre: nada de lo que ya
  funcionaba cambia.

> Un mob de jugador **sin dueno con faccion** no hereda nada: se comporta como antes.

## Ver los bandos de un vistazo (brillo por faccion)

Con `faction-glow.mode` en `global` (o en `player`, y cada quien lo enciende con
`/custommobs glowfaccion`) cada jugador ve a los demas con el brillo de su relacion:

| Relacion contigo | Brillo |
|---|---|
| Facción enemiga | **rojo** |
| Tu facción o una aliada | **verde** |
| Sin relacion, o sin faccion | **gris** |

Asi las facciones PvE tienen sentido tambien en el PvP: antes de pegar, se ve a quien.

El color funciona **por espectador**: el mismo jugador puede salirte verde a ti y rojo a otro.
Se hace mandando equipos falsos con **PacketEvents** (no se toca el scoreboard del servidor, asi
que no interfiere con Teams). Detalles en `configuracion.md`.

## Teams y facciones, de acuerdo

Cuando a un jugador se le asigna faccion, el plugin **impide** que quede en un **team cuyo jefe
es de otra faccion**: se rechaza la entrada — y tambien la del jefe cuyo team tenga gente de otra
faccion. No se saca a nadie: la incoherencia se corta antes de producirse. Y al conectarse se
repasa el team, avisando en el log si quedo incoherente. Se configura en `configuracion.md`
(`teams`).

## Como se ven los mobs de servidor

Cada faccion puede declarar en `factions.yml` como se ven **sus mobs de categoria `server`**:

```yaml
orcos:
  allies: [goblins]
  enemies: [humanos, enanos]
  color: DARK_GREEN   # color del nombre
  glow: GREEN         # color del brillo; ademas lo enciende
```

Los dos son opcionales. Los mobs de **jugador** no los usan: esos llevan el color que elija su
dueno o su team con `/cm color` y `/cm glow`.

> Los mobs **disfrazados** (LibsDisguises) no muestran estos colores: con un disfraz, el cliente
> pinta otra entidad y nuestros datos no llegan. Para un mob que tenga que lucir su color de
> faccion, quitale el disfraz.

## Fuego amigo entre jugadores

Dos jugadores del **mismo bando** no se hacen dano: ni de la misma faccion, ni de facciones
**aliadas**, ni companeros del mismo **team**. El golpe se anula sin mas. Solo vale dentro de
los mundos donde trabaja el plugin; fuera de ellos manda el PvP del servidor.

## Teams (opcional)

Con **Teams** instalado, los jugadores se agrupan en bandos:

- Un mob protege a los companeros de team de su dueno.
- `targets.players: enemies` usa los enemigos que declare Teams.
- El **color y el brillo** de los player mobs se ajustan **por team**: lo decide el jefe
  y aplica a los mobs de todos los miembros. Sin Teams, cada jugador manda sobre los suyos.

Sin Teams instalado, cada jugador solo se protege a si mismo y `enemies` equivale a
`never`.
