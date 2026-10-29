# Tutorial 10 — Control de masas para PvP

Cuatro controles comparten el mismo servicio: `stun`, `root`, `silence` y `disarm`. Todos
duran exactamente lo que diga `amount` en segundos; el cooldown es `cooldown-seconds`.
No hay retorno decreciente: el equilibrio se ajusta en el yml de cada skill.

## Que bloquea cada uno

| Control | Mover | Atacar | Items | Disparar |
|---|---|---|---|---|
| `stun` | no | no | no | no |
| `root` | no | si | si | si |
| `silence` | si | si | no | no |
| `disarm` | si | no | si | no |

## Un aturdimiento

```yaml
# skills/golpe_paralizante.yml
mode: activa
effect: stun
target-type: look
target: enemies
range: 20
amount: 1.5
cooldown-seconds: 20
```

`mode: activa` solo entra en combate. Para una trampa de area, cambia a
`target-type: radius` con `target: enemies` y un `range` corto.

### Mobs con modelo

Si el mob tiene un modelo de BetterModel, aturdirlo congela tambien el modelo: deja de
alternar `idle`/`walk` y no se mueve aunque algo empuje a la entidad. El mob aturdido
tampoco golpea, ni cuerpo a cuerpo ni con flecha. Necesita que el modelo no tenga
`sight-trace: true`: con ese ajuste BetterModel orienta el modelo hacia el jugador por su
cuenta y el aturdido sigue girando. Va apagado por defecto.

## Raiz, silencio y desarme

```yaml
# skills/raices.yml
mode: activa
effect: root
target-type: radius
target: enemies
range: 4
amount: 2.5
cooldown-seconds: 25
```

```yaml
# skills/silencio.yml
mode: activa
effect: silence
target-type: look
target: enemies
amount: 3
cooldown-seconds: 30
```

```yaml
# skills/desarme.yml
mode: activa
effect: disarm
target-type: look
target: enemies
amount: 2
cooldown-seconds: 25
```

## Romper el control con dano

`cancel-on-hit: true` hace que el control se rompa cuando el objetivo recibe dano. El golpe
que lo aplica no lo rompe: el evento de dano ya paso. Sirve para controles de apertura que
no deben encadenarse.

```yaml
- id: golpe_paralizante
  mode: activa
  effect: stun
  target-type: look
  target: enemies
  amount: 3
  cooldown-seconds: 20
  cancel-on-hit: true
```

El equivalente para el sigilo es `reveal-on-attack: true`: el jugador se muestra al hacer
daño. Se explica en `03-skills.md`.

## Escudo de inmunidad

`cc_immune` no limpia el control activo: impide que te apliquen uno nuevo mientras dure.
La duracion sale de `amount`; si lo dejas en `0`, usa `cc.shield-seconds` del config.

```yaml
# skills/escudo_cc.yml
mode: item
effect: cc_immune
target: self
amount: 8
cooldown-seconds: 60
```

```yaml
# items/amuleto_escudo.yml
material: SHIELD
stun-free: true
usar:
  skills: [escudo_cc]
  cooldown: 60
```

## Trinket que rompe el control

`cleanse_cc` quita todos los controles y da inmunidad. `amount` son los segundos de
inmunidad. El objeto necesita `stun-free: true` para poder usarse estando aturdido o
silenciado.

```yaml
# skills/rompe_control.yml
mode: item
effect: cleanse_cc
target: self
amount: 1
cooldown-seconds: 120
```

```yaml
# items/trinket_pvp.yml
material: NETHER_STAR
clase: cazador
stun-free: true
usar:
  skills: [rompe_control]
  cooldown: 120
```

## Configuracion

```yaml
# config.yml
cc:
  immunity-seconds: 1
  shield-seconds: 8
  shield-message: '&8Estas protegido contra controles.'
  break-message: '&8El golpe rompe el control.'
```

- `immunity-seconds`: inmunidad al salir de un control; `0` la desactiva.
- `shield-seconds`: duracion por defecto de `cc_immune` cuando la skill no trae `amount`.
- Los mensajes de cada control (`stun-message`, `root-message`, etc.) tambien viven ahi.

## Como se implementa

No se teletransporta ni se tocan paquetes: se ponen a cero `MOVEMENT_SPEED` y
`JUMP_STRENGTH` del jugador, y se cancelan ataques, items y disparos. Asi el anticheat no
ve movimientos raros. A los mobs se les apaga la IA.
