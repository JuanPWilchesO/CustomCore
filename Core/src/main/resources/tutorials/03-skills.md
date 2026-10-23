# Tutorial 3 — Skills

Vamos a darle al arquero cuatro habilidades: un golpe fuerte, un grito, una curacion a su
dueno y un veneno a los enemigos.

## 1. El mob

`mobs/arquero.yml`:

```yaml
id: arquero
category: player
display-name: '&eArquero'
type: SKELETON
egg: SKELETON_SPAWN_EGG

recipe:
  extras:
    - REDSTONE
  amount: 1

attributes:
  health: 30.0
  damage: 3.0

targets:
  players: defensive

skills:
  - id: flechazo
    effect: damage
    target: target
    range: 4.0
    amount: 8.0
    cooldown-seconds: 6
    chance: 0.5
    message: '&eEl arquero te clava una flecha.'

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

  - id: flecha_venenosa
    effect: potion
    target: enemies
    range: 8.0
    potion: poison
    potion-duration-seconds: 6
    potion-amplifier: 0
```

## 2. Los efectos

| `effect` | Hace |
|---|---|
| `damage` | resta `amount` de vida |
| `heal` | suma `amount` de vida; con `porcentaje: true`, es fraccion de la vida maxima |
| `ignite` | prende fuego durante `amount` segundos |
| `freeze` | congela durante `amount` segundos y aplica Lentitud II |
| `message` | manda un texto a los jugadores en rango |
| `potion` | aplica `potion` durante `potion-duration-seconds` |
| `repair_all` | repara todo el inventario/equipo con durabilidad |
| `teleport_behind` | aparece detrás del objetivo, mirándolo |
| `backstep` | retrocede y mira al punto de origen |

## Sigilo desde una skill

`potion: sigilo` aplica el efecto de `effects.yml` con `mecanica: sigilo`. Para que el
jugador se muestre al atacar, añade `reveal-on-attack: true` a la **skill**:

```yaml
  - id: sombra
    effect: potion
    target: self
    potion: sigilo
    potion-duration-seconds: 30
    reveal-on-attack: true
```

- `reveal-on-attack` es opcional; por defecto es `false` y el sigilo aguanta hasta caducar.
- Si es `true`, cualquier daño que haga el jugador (cuerpo a cuerpo o proyectil) le quita
  el sigilo y vuelve a ser visible.
- El `potion` tiene que ser el id del efecto con `mecanica: sigilo` (por defecto, `sigilo`).

## Avisos de la skill

Una skill casteada por un jugador avisa en pantalla, no por chat:

```yaml
  - id: manto
    effect: potion
    target: self
    potion: invulnerabilidad
    potion-duration-seconds: 10
    inicio: '&bManto activado.'
    fin: '&7El manto se apagó.'
```

- `inicio` se muestra al lanzarla; `fin`, cuando caduca el efecto del plugin.
- Si la skill aplica un efecto del plugin (`potion:`), `inicio` no se muestra: la boss bar
  del efecto ya lo representa. `fin` sí sigue saliendo al caducar.
- `fin` no vale para efectos instantáneos como `damage` o `heal`.
- `message-display` decide: `auto`, `chat` o `screen`.
- El cooldown es visual; `cooldown-aviso` ya no se usa.
- Los avisos van a quien castea, no al objetivo.

## Conversaciones y comandos

Con `mode: interaccion` puedes montar un **menu**: el jugador hace clic derecho en el mob y
arranca una conversacion con botones, que puede cobrar o ejecutar comandos.

```yaml
skills:
  - id: mision
    mode: interaccion
    effect: chain
    steps:
      - id: saludo
        message: '&e{mob}: Buenas, {jugador}.'
        next: pregunta
      - id: pregunta
        message: '&e¿Te doy la llave?'
        options:
          - label: '&a[ Si ]'
            next: entrega
          - label: '&c[ No ]'
            end: true
      - id: entrega
        console-command: 'give {jugador} tripwire_hook 1'
        message: '&aToma.'
        end: true
```

Los comandos (`console_command`, `player_command`) valen con `mode: interaccion` o
`mode: item`; fuera de ahi se descartan y el log lo avisa. Las cadenas (`chain`) solo
valen con `mode: interaccion`.

## Invocar a otros

Un mob puede **traer refuerzos**: con `effect: summon` invoca copias de otra definicion a su
alrededor.

```yaml
skills:
  - id: invocar
    mode: pasiva
    effect: summon
    summon: esbirro     # el id de la definicion que aparece
    amount: 3           # cuantas (tope 10 por uso)
    range: 6.0          # radio donde aparecen
    cooldown-seconds: 30
```

El `target` se ignora: siempre invoca alrededor del propio mob. Los invocados toman el dueno
del invocador. Ver `../docs/skills.md`.

## 3. Objetivos: como y a quien

Ahora se separan dos cosas:

```yaml
- id: prender
  effect: ignite
  target-type: LOOK      # LOOK | RADIUS
  target: ENEMIES        # ENEMIES | ALLIES | SELF
  range: 30
  amount: 5
```

- `LOOK + ENEMIES`: solo lo que apuntas, si es enemigo.
- `LOOK + ALLIES`: solo lo que apuntas, si es aliado.
- `RADIUS + ENEMIES/ALLIES`: todos los del bando en el radio.
- `LOOK/RADIUS + SELF`: el lanzador.

Asi una skill de mirada no puede hacer fuego amigo. Las skills viejas con `target: look`
se interpretan como `LOOK + ENEMIES`; `target: enemies/allies/self` siguen siendo de radio.
`owner` y `target` siguen valiendo como objetivos especiales de mob.

En `effect: message` el objetivo **se ignora**: habla a quien este en rango.

## 4. Detalles que importan

- `range` tiene tope de **128 bloques**.
- `chance: 0.5` = acierta la mitad de las veces; si falla, **no gasta** el cooldown.
- Los objetivos usan **la misma politica de combate que la IA**: si el mob te considera
  enemigo, sus skills tambien.
- El intervalo de evaluacion es `skills.interval-ticks` del config (20 = 1 segundo).

## 5. Prueba

```
/customcore reload
/customcore give arquero
```

Invocalo y dejate ver: debe gritarte, curarte si te haces dano y envenenar a quien lo
agreda.

Para probar una skill de item, mira `../tutorials/05-objeto-con-nbt.md`: un objeto puede
lanzar skills con `usar.skills`, y las skills compartidas viven en `skills/`.
