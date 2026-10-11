# Skills

Una skill es algo que el mob hace solo: golpear mas fuerte, curar a su dueno, avisar por
chat o envenenar a los enemigos. Se declaran en una lista dentro del mob.

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

## Campos

| Campo | Valores | Por defecto |
|---|---|---|
| `id` | texto unico dentro del mob | — |
| `effect` | `damage` \| `heal` \| `message` \| `potion` \| `summon` | — |
| `target` | `self` \| `owner` \| `target` \| `enemies` \| `allies` | — |
| `mode` | `pasiva` \| `activa` \| `interaccion` | `pasiva` |
| `range` | bloques (tope 32) | `8.0` |
| `cooldown-seconds` | entero | `5` |
| `chance` | `0.0` a `1.0` | `1.0` |
| `amount` | dano o curacion | `0.0` |
| `message` | texto con `&` | — |
| `potion` | `poison`, `speed`, `weakness`… | — |
| `summon` | la definicion que invoca (SUMMON), tal como su `id` | — |
| `potion-duration-seconds` | entero | `5` |
| `potion-amplifier` | entero (`0` = nivel I) | `0` |

## Cuando se dispara (`mode`)

| `mode` | Cuando entra a funcionar |
|---|---|
| `pasiva` | **siempre**, mientras el mob este cargado. Es el valor por defecto |
| `activa` | solo **en combate**, es decir, cuando el mob tiene a alguien a quien atacar |
| `interaccion` | cuando un jugador hace **clic derecho** sobre el mob |

```yaml
skills:
  # Pasiva: se evalua cada ciclo mientras el mob este cargado.
  - id: vendaje
    mode: pasiva
    effect: heal
    target: owner
    amount: 6.0
    cooldown-seconds: 25

  # Activa: solo entra cuando el mob esta peleando.
  - id: golpe_pesado
    mode: activa
    effect: damage
    target: target
    amount: 9.0
    cooldown-seconds: 8

  # Interaccion: se dispara con clic derecho y sirve de dialogo.
  - id: saludo
    mode: interaccion
    effect: message
    target: self
    cooldown-seconds: 3
    message: '&e{mob}&f: Buenas, {jugador}.'
```

En las skills de **interaccion**, `effect: message` va **solo a quien hizo clic** (no a todo
el que este cerca), y el texto admite `{jugador}` ademas de `{mob}`. Si el jugador trae un
huevo custom en la mano, ese clic es del huevo: la interaccion no se dispara.

## Comandos (`console_command`, `player_command`)

```yaml
skills:
  - id: menu
    mode: interaccion
    effect: player_command
    command: 'menu'

  - id: premio
    mode: interaccion
    effect: console_command
    command: 'give {jugador} diamond 1'
    message: '&aToma, te lo ganaste.'
```

| Campo | Que hace |
|---|---|
| `command` | el comando, sin la barra. Admite `{mob}` y `{jugador}` |

**Solo funcionan con `mode: interaccion`.** En `pasiva` o `activa` (combate) la skill **se
descarta al cargar**, con un aviso en el log: un mob que ejecuta comandos por su cuenta —y mas
en mitad de una pelea— es un agujero de seguridad, no una funcion. Dentro de una cadena
tambien valen, porque una cadena entera nace de un clic.

Con un clic, **el actor es quien hace clic**: `{jugador}` es el, aunque el `target` diga otra
cosa.

## Cadenas de interaccion (`effect: chain`)

Una conversacion por pasos. Cada paso dice algo, pregunta con botones, cobra o ejecuta un
comando, y decide a donde sigue.

```yaml
skills:
  - id: mision
    mode: interaccion          # una cadena SIEMPRE nace de un clic
    effect: chain
    steps:
      - id: saludo
        message: '&e{mob}: Buenas, {jugador}.'
        next: pregunta

      - id: pregunta
        message: '&e¿Me ayudas?'
        options:               # botones clicables
          - label: '&a[ Claro ]'
            next: pago
          - label: '&c[ Ahora no ]'
            end: true

      - id: pago
        pay: 100               # exige 100 monedas para seguir
        message: '&eNecesito 100 monedas.'
        next: premio           # si las tiene
        no-pay: sin_dinero     # si no las tiene

      - id: premio
        console-command: 'give {jugador} diamond 1'
        message: '&aHecho.'
        end: true

      - id: sin_dinero
        message: '&cVuelve cuando tengas.'
        end: true
```

| Campo del paso | Que hace |
|---|---|
| `id` | nombre del paso, unico en la cadena |
| `message` | lo que dice al llegar |
| `options` | botones: `label`, `next`, `end` |
| `next` | a donde sigue sin preguntar |
| `end` | cierra la conversacion |
| `pay` | cuanto dinero exige para seguir |
| `no-pay` | a donde va si **no** puede pagar (o `end: true` por otro lado) |
| `faction` | faccion que se le exige al jugador (`*` = que tenga cualquiera) |
| `no-faction` | a donde va si **no** cumple la faccion |
| `console-command` / `player-command` | lo que ejecuta el paso |

- El jugador avanza **pulsando los botones** del chat.
- El **paso no viaja en el clic**: se usa el que el servidor tiene apuntado para ese jugador, asi
  que nadie puede saltarse un paso —ni un pago— escribiendo el comando a mano.
- Una conversacion es de un jugador: empezar otra cierra la anterior. No sobrevive a un reinicio.

### Comprobar la faccion del jugador

```yaml
      - id: solo_del_bando
        faction: orcos        # o '*' para exigir simplemente que tenga alguna
        message: '&eVeo que eres de los nuestros...'
        next: mision
        no-faction: forastero # ← que pasa si no la cumple
```

- `faction: '*'` pide **que tenga alguna**, sea la que sea.
- Con una faccion concreta, valen tambien sus **aliadas**: quien sirve a los orcos sirve a los
goblins.
- `no-faction` sin poner, o apuntando a un paso que no existe, **cierra la conversacion**.

> **Aviso sobre los pagos.** Las skills de pago (`pay`) **no funcionan sin un plugin de
economia**. El plugin se engancha a **EssentialsX** (por reflexion, asi no depende de el para
compilar). Sin EssentialsX, **todo pago falla**: la cadena tomara siempre la rama de `no-pay` y
el jugador no pagara nada. El plugin lo avisa en el log al arrancar.

## Invocar (`effect: summon`)

El mob **invoca copias de otra definicion a su alrededor**. No va contra un objetivo: usa
`amount` como cuantas invoca y `range` como el radio donde aparecen.

```yaml
skills:
  - id: invocar
    mode: pasiva
    effect: summon
    target: self        # se ignora: siempre invoca alrededor del propio mob
    summon: esbirro     # el id de la definicion que aparece
    amount: 3           # cuantos (por defecto 1; tope 10 por uso)
    range: 6.0          # radio donde aparecen
    cooldown-seconds: 30
```

- Los invocados toman el **dueno del invocador**: si el que invoca es de un jugador, los suyos
  tambien. Un mob de servidor invoca mobs libres.
- Aparecen en el **suelo mas alto** de su columna, buscando un hueco con suelo solido debajo;
  si no encuentran sitio, **no se gasta el cooldown** y se reintenta.
- El tope de 10 por uso esta para que un yml descuidado no suelte una horda: el ritmo lo marca
  `cooldown-seconds`.

## Los cinco efectos

| `effect` | Hace |
|---|---|
| `damage` | resta `amount` de vida al objetivo |
| `heal` | suma `amount` de vida al objetivo |
| `message` | manda un texto a los jugadores en rango |
| `potion` | aplica `potion` durante `potion-duration-seconds`, a nivel `potion-amplifier` |
| `summon` | invoca copias de otra definicion alrededor del mob |
| `console_command` | ejecuta un comando desde la consola (**solo interaccion**) |
| `player_command` | el jugador ejecuta un comando (**solo interaccion**) |
| `chain` | una conversacion por pasos, a base de clics (**solo interaccion**) |

## Los cinco objetivos

| `target` | Quien es |
|---|---|
| `self` | el propio mob |
| `owner` | su dueno |
| `target` | a quien esta atacando ahora |
| `enemies` | los enemigos declarados **y** quien lo agredio (venganza) |
| `allies` | companeros de team y aliados |

**Los objetivos usan la misma politica de combate que la IA**: si el mob considera
enemigo a alguien, sus skills tambien. No hay dos definiciones de "enemigo".

### Objetivos en mobs de servidor

Los mobs de `category: server` **no tienen dueno**, y eso cambia los objetivos:

- `owner` **nunca resuelve**: sin dueno, la skill no hace nada. No la uses ahi.
- `enemies` es faccion enemiga, o quien lo agredio; contra jugadores manda `attitude`.
- `allies` alcanza **solo a las facciones declaradas aliadas** en `factions.yml`. Ojo: la
  **misma faccion no cuenta como aliada** aqui, aunque en combate si es intocable.
- `target` funciona igual, pero necesita que el mob este peleando.

## Detalles que importan

- En `effect: message` el `target` **se ignora**: el mob habla a los jugadores dentro
  de `range`. `{mob}` se sustituye por su nombre.
- `message` tambien funciona en `damage`, `heal` y `potion`: si el objetivo es un
  jugador, le llega el aviso. Es la forma de que una skill sea **observable**.
- Si falla la tirada de `chance`, se reintenta en el siguiente ciclo **sin gastar** el
  cooldown.
- `range` tiene un tope duro de **32 bloques**; lo que pidas por encima se recorta.
- `range` es **alcance maximo tambien para `owner` y `target`**: una skill de dueno no
  alcanza a su dueno si esta fuera del radio, ni una de objetivo a un enemigo lejano. El
  valor por defecto son **8 bloques**.
- Una skill mal escrita **se descarta sola**. Nunca tumba la carga del mob.
- El intervalo de evaluacion es `skills.interval-ticks` del config (20 = 1 segundo).
