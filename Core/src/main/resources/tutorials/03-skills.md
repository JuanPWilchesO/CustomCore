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

## 2. Los cuatro efectos

| `effect` | Hace |
|---|---|
| `damage` | resta `amount` de vida |
| `heal` | suma `amount` de vida |
| `message` | manda un texto a los jugadores en rango |
| `potion` | aplica `potion` durante `potion-duration-seconds` |

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

Los comandos (`console_command`, `player_command`) **solo** valen con `mode: interaccion`:
fuera de ahi se descartan y el log lo avisa.

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

## 3. Los cinco objetivos

`self`, `owner`, `target`, `enemies`, `allies`. En `effect: message` el objetivo
**se ignora**: habla a quien este en rango.

## 4. Detalles que importan

- `range` tiene tope de **32 bloques**.
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
