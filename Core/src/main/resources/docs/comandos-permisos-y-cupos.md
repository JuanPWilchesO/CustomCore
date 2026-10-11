# Comandos, permisos y cupos

Todos bajo `/custommobs` (alias `cmobs`, `cm`).

| Comando | Que hace |
|---|---|
| `reload` | recarga mobs, config y facciones sin reiniciar |
| `list` | lista las definiciones cargadas |
| `give <id> [jugador]` | entrega el huevo de ese mob |
| `spawn <id> [mundo x y z]` | invoca el mob. Desde el juego, tu quedas como dueno |
| `kill <id>` | mata todos los mobs de esa definicion |
| `remove [radio]` | retira los mobs custom cercanos (solo desde el juego) |
| `active` | lista los mobs vivos, con dueno y ubicacion |
| `enchants [filtro]` | lista encantamientos disponibles (util para validar ids) |
| `cuota [jugador]` | consulta el cupo y **lista tus mobs** con su id y ubicacion |
| `cuota recontar <jugador>` | rehace la cuenta **sin tocar mobs** |
| `cuota retirar <jugador>` | retira sus mobs y deja el cupo en cero (**destructivo**) |
| `despedir <id\|todos>` | retira **tus** mobs; lo usa cualquier jugador con los suyos, y el id se autocompleta |
| `faction set <jugador> <faccion>` | asigna faccion a un jugador (solo admin) |
| `faction get <jugador>` | consulta su faccion |
| `faction clear <jugador>` | se la quita |
| `faction list` | lista los jugadores con faccion |
| `glowfaccion` | (cualquier jugador) enciende o apaga su brillo de facciones |
| `item save <nombre>` | guarda el objeto de tu mano en el catalogo, con su NBT |
| `item list` | lista los objetos guardados |
| `item remove <nombre>` | borra uno del catalogo |
| `color <color\|nada>` | color del nombre de tus player mobs |
| `glow <color\|nada>` | brillo de tus player mobs |
| `spawner list` | lista los spawners: id, mob, mundo, coordenadas y reaparicion |
| `spawner remove <id>` | borra un spawner y, si esta, su mob vivo |
| `spawner removeall [mob]` | borra todos los spawners, o solo los de un mob |
| `spawner reload` | relee `data/spawners.yml` sin reiniciar |
| `book [jugador]` | entrega el libro de inspeccion (alias `libro`) |
| `upgrade list` | lista los items de mejora cargados |
| `upgrade give <id> [jugador]` | reparte un item de mejora (alias `mejora`) |
| `upgrade reload` | relee `upgrades/` y rehace las recetas |

Desde consola, `spawn` pide mundo y coordenadas: `/custommobs spawn <id> <mundo> <x> <y> <z>`.

**`recontar` vs `retirar`** — la diferencia es importante:

- `recontar`: no mata nada. Deja en la cuenta solo los mobs que se ven ahora. Una entrada
  de un mob que ya no existe desaparece y no vuelve; la de un mob real que esta en un chunk
  descargado **volvera a contar** cuando su chunk cargue. Sirve para corregir cuentas infladas.
- `retirar`: **mata** los mobs que alcanza y deja el cupo en cero. Los que estan anclados en
  chunks descargados no se pueden tocar hasta que carguen.

## Permisos

| Permiso | Para que | Por defecto |
|---|---|---|
| `custommobs.player` | invocar mobs con los huevos | `true` |
| `custommobs.admin` | comandos de administracion | `op` |

Un jugador raso puede hacer **cuatro cosas** por su cuenta, y nada mas:

- `cuota` **sin argumentos** — ver **su propio** cupo.
- `color <color|nada>` — el color del nombre de **sus** mobs.
- `glow <color|nada>` — el brillo de **sus** mobs.
- `despedir <id|todos>` — retirar **sus** mobs. Es su via para deshacerse de ellos: si no,
  no tendria forma de bajar su propio cupo.

Con Teams, `color` y `glow` los decide **solo el jefe del team** y aplican a los mobs de
todos sus miembros.

Todo lo demas —incluido `cuota <jugador>`, `cuota recontar` y `cuota retirar`— exige
`custommobs.admin`.

> **Ojo con `default: false`**: a diferencia de `op`, **ignora** el flag de operador. Si lo
> cambias, ni tu (siendo op) podreis invocar hasta que se os conceda el permiso.

Los permisos van por el sistema **nativo de Bukkit**, asi que sirve cualquier plugin de
permisos, no solo LuckPerms.

## Cupo de mobs por grupo

En `config.yml`:

```yaml
limits:
  default-player-mobs: 3   # mobs que te siguen (anchor: owner)
  default-point-mobs: 1    # mobs fijos a un bloque (anchor: point)
  default-mount-mobs: 1    # monturas (category: mount)
  groups:
    default: 3
    vip: 5
    staff: 10
  point-groups:
    default: 1
    vip: 2
    staff: 3
  mount-groups:
    default: 1
    vip: 2
    staff: 3
```

El cupo esta **partido en tres cuentas que no se pisan**: los mobs que te siguen, los
fijos a un bloque y las monturas. Puedes llevar una llena y seguir colocando de las otras.
`/custommobs cuota` muestra las tres.

- El grupo se lee de **LuckPerms en el momento de invocar**, no al conectarse: un cambio de
  rango aplica al instante.
- Si el jugador pertenece a **varios grupos, gana el cupo mas alto**.
- Si su grupo no esta mapeado, o no se pudieron leer sus grupos, se usa
  `default-player-mobs`. `0` = sin limite.
- Sin LuckPerms instalado, todos caen al grupo `default`.
- Cambiar `limits` aplica con `/custommobs reload`, **sin reiniciar**.

### Mobs fijos: su zona se mantiene cargada

Un mob de jugador con `anchor: point` mantiene cargada una zona a su alrededor, para no
desaparecer ni dejar de contar aunque no haya nadie cerca. El radio sale de
`point.chunk-radius` del config, y cada mob puede sobrescribirlo con su propio
`chunk-radius`. Solo se suelta cuando lo recoges con su huevo.
