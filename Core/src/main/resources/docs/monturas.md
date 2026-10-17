# Monturas

Una montura es un mob custom de `category: mount`: un caballo, burro, mula, caballo
esqueleto, llama o camello que el jugador **monta**, con sus propios efectos, su propio
paso helado y **su propia cuenta de cupo**.

```yaml
id: corcel_negro
category: mount
display-name: '&5Corcel negro'
type: HORSE
egg: HORSE_SPAWN_EGG

attributes:
  health: 60.0
  speed: 0.3
  jump-strength: 0.9

mount:
  color: BLACK
  style: WHITE_DOTS
  saddled: true
  tamed: true
  armor: DIAMOND_HORSE_ARMOR
  frost-walker: true
  effects:
    - potion: fire_resistance
    - potion: conduit_power
      to: rider
    - potion: speed
      to: nearby
      radius: 8.0
      amplifier: 1
```

> `category: mount` exige una entidad que sea un caballo o similar (`AbstractHorse`).
> Con otro `type` se carga como un mob normal.

## La seccion `mount`

| Campo | Valores | Por defecto |
|---|---|---|
| `color` | color de caballo: `BLACK`, `WHITE`, `CHESTNUT`, `CREAMY`, `DARK_BROWN`, `GRAY`, `BROWN` | el de serie |
| `style` | marcas: `NONE`, `WHITE`, `WHITE_DOTS`, `WHITEFIELD`, `BLACK_DOTS` | el de serie |
| `saddled` | `true` \| `false` | `true` |
| `tamed` | `true` \| `false` | `true` |
| `armor` | material de armadura de caballo | ninguna |
| `frost-walker` | `true` \| `false` | `false` |
| `effects` | lista de efectos constantes | vacia |

`color` y `style` solo tienen sentido en un `HORSE`. `tamed: true` es lo que permite
subirse: sin domesticar, el jugador no puede montarla. La silla (`saddled`) tambien hace
falta.

## Efectos constantes (`mount.effects`)

Cada efecto se vuelve a aplicar cada segundo con una duracion algo mayor que el ciclo, asi
que **no parpadea**: mientras la montura exista, el efecto esta puesto. Van **sin particulas
ni icono**, porque no son una pocion que alguien haya bebido.

| Campo | Valores | Por defecto |
|---|---|---|
| `potion` | id de efecto (`FIRE_RESISTANCE`, `SPEED`, `CONDUIT_POWER`…) | — |
| `to` | `mount` \| `rider` \| `nearby` | `mount` |
| `amplifier` | entero (`0` = nivel I) | `0` |
| `radius` | bloques; **solo para `to: nearby`** | `8.0` |
| `visible` | `true` \| `false` (muestra particulas e icono) | `false` |

### A quien se le da

| `to` | Quien lo recibe |
|---|---|
| `mount` | la propia montura |
| `rider` | **quien la monta**, y solo mientras la monta |
| `nearby` | los jugadores dentro de `radius` |

En `rider` y `nearby` **no vale cualquiera**: solo el **dueno**, sus **companeros de team** y
sus **aliados**. Regalar resistencia al fuego a un desconocido no es lo que se pidio.

- `rider` alcanza a quien la monte si es del bando; si te bajas, dejas de recibirlo al
  instante.
- `nearby` respeta `radius`. Es un radio real: fuera de el, no llega.

## Paso helado (`frost-walker`)

Con `frost-walker: true`, el agua que la montura pisa **se convierte en hielo escarchado**,
como el encantamiento de botas. Se hiela el bloque justo bajo las patas —y los contiguos,
como el encantamiento— y el hielo **se derrite solo a los ~20 segundos**.

No es una pocion (eso no existe): es el efecto del encantamiento hecho a mano.

### Aviso: funcion experimental

**El paso helado puede fallar, y no es un fallo que se arregle con configuracion: depende del
rendimiento del servidor.**

El hielo tiene que estar puesto **antes** de que la montura llegue, y eso se decide cada
tick. Si el servidor va cargado (TPS bajo), o hay **lag de red**, el hielo llega tarde: la
montura pisa agua que ya deberia estar helada y se cae. A galope se nota mucho mas que al
paso, y con ping alto es esperable que falle directamente.

Reglas que conviene conocer antes de usarlo:

- **Solo hiela la superficie** del agua (la que tiene aire encima), como el encantamiento.
- **Nunca hiela a la altura de las patas ni por encima**, para no encerrar a la montura.
- **Mientras la montura ya esta dentro del agua no hiela nada** a su alrededor: helar a su
altura le levantaria un pozo rodeado de hielo del que, nadando, no puede salir.
- **No se le puede ajustar el area a mano.** El plugin decide por su cuenta cuanto hielo
  poner, segun la velocidad de la montura y el retraso del servidor, buscando el mejor
  equilibrio entre llegar a tiempo y no hundir el servidor. Cuanto mejor vaya el servidor,
  mejor funciona.

Las claves `frost-radius` y `frost-ahead` se siguen leyendo del yml, pero **hoy no se usan**:
se reservan por si hacen falta mas adelante.

## Las monturas NO se teletransportan

Es la diferencia con los demas mobs anclados al dueno, y es deliberado:

- Un mob normal (`anchor: owner`) **se teletransporta** a tu lado si lo dejas atras.
- Una **montura no**. Si la dejas, **se queda donde esta**. Una montura que aparece sola
  detras de ti es una montura que no esperabas: aqui eso no pasa.

Si no vuelves a por ella ni la recoges, entra la **ventana de abandono** (`recall`): recibe
un **aviso por chat** con su plazo (60 s por defecto) y, si no vuelves, **se retira** y su
huevo queda inerte. Es el precio de no arrastrarla: puedes perderla, y el aviso existe para
que no te enteres tarde. Ver `configuracion.md`.

## Recogerla

Igual que cualquier mob de jugador: **golpeala con su propio huevo** y vuelve al huevo, con
su vida y sus mejoras. Colocala de nuevo y sigue siendo tuya.

## Cupo: una tercera cuenta

Las monturas tienen **su propia cuenta de cupo**, aparte de las otras dos:

```yaml
limits:
  default-mount-mobs: 1
  mount-groups:
    default: 1
    vip: 2
    staff: 3
```

Asi puedes llevar el cupo de seguidores y el de mobs fijos llenos y, ademas, tu montura.
`/customcore cuota` muestra las **tres** lineas. `0` = sin limite.

---

## Ver tambien

- `el-archivo-de-un-mob.md` — el resto de claves de `mobs/*.yml`.
- `configuracion.md` — `limits`, `recall` y el resto del config.
- `comandos-permisos-y-cupos.md` — la tercera cuenta de cupo y los comandos.
