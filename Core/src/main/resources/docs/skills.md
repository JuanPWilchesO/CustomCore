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

## Modelo de objetivos: `target-type` + `target`

Una skill decide **como** elige el objetivo y **a quien** se lo aplica. Son dos claves
separadas:

```yaml
target-type: LOOK      # LOOK | RADIUS
target: ENEMIES        # ENEMIES | ALLIES | SELF
```

| Combinacion | Quien recibe la skill |
|---|---|
| `LOOK + ENEMIES` | solo la entidad de la cruz de vista, si es enemiga |
| `LOOK + ALLIES` | solo la entidad de la cruz de vista, si es aliada |
| `LOOK + SELF` | el lanzador |
| `RADIUS + ENEMIES` | todos los enemigos dentro de `range` |
| `RADIUS + ALLIES` | todos los aliados dentro de `range` |
| `RADIUS + SELF` | el lanzador |

Asi una skill de mirada **no puede hacer fuego amigo**: si apuntas a un aliado con
`target: ENEMIES`, no hace nada.

**Compatibilidad**: las skills viejas siguen cargando. `target: enemies/allies/self`
se interpreta como `RADIUS + ...`; `target: look` se interpreta como
`LOOK + ENEMIES` (ya no golpea aliados). `target: owner` y `target: target` siguen
siendo objetivos especiales de mob.

## Campos

| Campo | Valores | Por defecto |
|---|---|---|
| `id` | texto unico dentro del mob | — |
| `effect` | `damage` \| `heal` \| `ignite` \| `freeze` \| `block_below` \| `explosion` \| `repair_all` \| `teleport_behind` \| `backstep` \| `message` \| `potion` \| `summon` \| `chain` \| `console_command` \| `player_command` | — |
| `target-type` | `LOOK` \| `RADIUS` | se infiere del `target` viejo |
| `target` | `ENEMIES` \| `ALLIES` \| `SELF`; legado: `owner` \| `target` \| `look` | `self` en cadenas, invocaciones y comandos; obligatorio en el resto |
| `mode` | `pasiva` \| `activa` \| `interaccion` \| `item` | `pasiva` |
| `range` | bloques (tope 128) | `8.0` |
| `cooldown-seconds` | entero | `5` |
| `chance` | `0.0` a `1.0` | `1.0` |
| `amount` | dano, curacion o potencia de explosion | `0.0` |
| `block` | material para `block_below`, ej. `MAGMA_BLOCK` | — |
| `block-radius` | radio del area de `block_below` (0-5; 0 = solo el bloque de abajo) | `0` |
| `block-depth` | profundidad de `block_below` (1-5) | `1` |
| `break-blocks` | si `explosion` rompe terreno | `false` |
| `distance` | distancia para `teleport_behind`/`backstep` (0.5-5) | `1.0` |
| `message` | texto con `&` | — |
| `inicio` | aviso al lanzar la skill (solo si castea un jugador) | — |
| `fin` | aviso cuando caduca el efecto del plugin que aplica la skill (solo si castea un jugador) | — |
| `cooldown-aviso` | aviso del objeto que la lanza al intentar usarlo en espera; admite `{segundos}` | — |
| `potion` | `poison`, `speed`, `weakness`… | — |
| `summon` | la definicion que invoca (SUMMON), tal como su `id` | — |
| `potion-duration-seconds` | entero | `5` |
| `potion-amplifier` | entero (`0` = nivel I) | `0` |

## Avisos al jugador

Las skills que castea un jugador pueden avisar por chat:

```yaml
- id: manto
  effect: potion
  target: self
  potion: invulnerabilidad
  potion-duration-seconds: 10
  inicio: '&bManto activado: 10 s.'
  fin: '&7El manto se apagó.'
  cooldown-aviso: '&cFaltan {segundos}s.'
```

- `inicio`: se manda al lanzar la skill. Solo si el lanzador es un jugador.
- `fin`: se manda cuando caduca el efecto del plugin que aplica la skill. No vale para
  efectos instantáneos como `damage` o `heal`.
- `cooldown-aviso`: lo usa el objeto que lanza la skill cuando está en espera. Admite
  `{segundos}`, `{tiempo}` o `{cooldown}`; también `{jugador}`.
- Los avisos van a quien castea, no al objetivo.
- Si el objeto define `usar.inicio` o `usar.cooldown-aviso`, esos mandan sobre los de la
  skill. El `usar.fin` del objeto solo aplica a `usar.efecto`; con `usar.skills`, el
  fin lo pone cada skill.

## Cuando se dispara (`mode`)

| `mode` | Cuando entra a funcionar |
|---|---|
| `pasiva` | **siempre**, mientras el mob este cargado. Es el valor por defecto |
| `activa` | solo **en combate**, es decir, cuando el mob tiene a alguien a quien atacar |
| `interaccion` | cuando un jugador hace **clic derecho** sobre el mob |
| `item` | solo lo lanza un objeto con `usar.skill`/`usar.skills`; el planificador de mobs lo ignora. Obligatorio para comandos y cadenas desde objetos |
| `impacto` | cuando un **proyectil** lanzado por el jugador toca algo. Solo desde objetos; el planificador de mobs lo ignora |

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

## Skills al impactar (`mode: impacto`)

Una skill con `mode: impacto` no se castea al usar el objeto: se **arma** cuando sale el
proyectil y se resuelve cuando toca. El lanzador sigue siendo el jugador; el origen pasa a
ser el punto de impacto.

```yaml
# skills/flecha_cura.yml
- id: flecha_cura
  mode: impacto
  effect: heal
  target-type: look
  target: impacto
  amount: 6
  cooldown-seconds: 8

# skills/bomba_cura.yml
- id: bomba_cura
  mode: impacto
  effect: heal
  target-type: radius
  target: allies
  range: 4
  amount: 5
```

| `target` | Con `target-type: look` | Con `target-type: radius` |
|---|---|---|
| `impacto` | la entidad golpeada | no vale: impacto es una sola entidad |
| `self` | el jugador que disparo | el jugador que disparo |
| `enemies` | la golpeada, si es enemiga | enemigos en `range` desde el impacto |
| `allies` | la golpeada, si es aliada | aliados en `range` desde el impacto, **incluido el lanzador si esta dentro** |

Reglas:

- Sin `target-type`, es **una sola entidad**: la golpeada. No hay area por accidente.
- `range` se mide desde el punto de impacto, no desde el jugador.
- Impacto en bloque: `target: impacto` no hace nada; `radius` si funciona, con el bloque
  como centro.
- El cooldown arranca **al disparar**, y el aviso de inicio tambien. Si el proyectil falla,
  el cooldown se gasto igual.
- `chance` se tira **al impactar**.
- Solo valen `impacto`, `self`, `enemies` y `allies`. `owner`, `target` y `look` se
  rechazan al cargar. `chain`, `summon`, `console_command` y `player_command` tampoco
  se admiten al impactar.
- Proyectiles cubiertos: flecha y ballesta, municion custom, tridente, bola de nieve y huevo.
  Cohete de ballesta, carga de viento, perla de ender y cana de pescar quedan fuera.
- Multishot: solo la **primera** flecha que impacta ejecuta la skill.

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

**Solo funcionan con `mode: interaccion` o `mode: item`.** En `pasiva` o `activa`
(combate) la skill **se descarta al cargar**, con un aviso en el log: un mob que ejecuta
comandos por su cuenta —y mas en mitad de una pelea— es un agujero de seguridad, no una
funcion. Dentro de una cadena tambien valen, porque una cadena entera nace de un clic.

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
    target: self        # opcional aqui: una cadena no va contra nadie
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
| `pay-item` | objeto del inventario que acepta como pago (material o id del catálogo) |
| `pay-item-count` | cuántos objetos pide (por defecto `1`) |
| `pay-xp` | niveles de experiencia que acepta como pago |
| `no-pay` | a donde va si **no** puede pagar con ninguna opción (o `end: true` por otro lado) |
| `faction` | faccion que se le exige al jugador (`*` = que tenga cualquiera) |
| `no-faction` | a donde va si **no** cumple la faccion |
| `console-command` / `player-command` | lo que ejecuta el paso |

Si un paso declara **varias** formas de pago, son **alternativas**: se intenta dinero,
luego objeto y luego niveles de XP; la primera que el jugador pueda pagar se consume. Si no
puede con ninguna, va a `no-pay`.

```yaml
- id: pago
  message: '&ePuedes pagarme 100 monedas, 3 diamantes o 5 niveles.'
  pay: 100
  pay-item: DIAMOND
  pay-item-count: 3
  pay-xp: 5
  next: premio
  no-pay: sin_pago
```

`pay-item` también acepta el **id de un objeto del catálogo**; en ese caso cuenta solo
copias de ese objeto (por su marca NBT), no un material parecido.

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

## Efectos

| `effect` | Hace |
|---|---|
| `damage` | resta `amount` de vida al objetivo |
| `heal` | suma `amount` de vida; con `porcentaje: true`, `amount` es fraccion de la vida maxima |
| `ignite` | prende fuego al objetivo durante `amount` segundos |
| `freeze` | congela al objetivo durante `amount` segundos y aplica Lentitud II |
| `block_below` | convierte los bloques bajo el objetivo en `block` |
| `explosion` | explosion al lado del objetivo; `amount` es la potencia (1-10) y `break-blocks` decide si rompe terreno |
| `repair_all` | repara todos los objetos con durabilidad del inventario/equipo del objetivo; respeta `reparar: false` |
| `teleport_behind` | teletransporta al lanzador detrás del objetivo, mirándolo; `distance` son bloques |
| `backstep` | retrocede al lanzador y lo deja mirando al punto de origen; `distance` son bloques |
| `message` | manda un texto a los jugadores en rango |
| `potion` | aplica `potion` durante `potion-duration-seconds`, a nivel `potion-amplifier` |
| `summon` | invoca copias de otra definicion alrededor del mob |
| `console_command` | ejecuta un comando desde la consola (**solo interaccion o item**) |
| `player_command` | el jugador ejecuta un comando (**solo interaccion o item**) |
| `chain` | una conversacion por pasos, a base de clics (**solo interaccion**) |

## Objetivos

Con el modelo nuevo, `target-type` elige **como** y `target` elige **a quien**:

| `target` | Quien es |
|---|---|
| `SELF` | el lanzador |
| `ENEMIES` | enemigos: faccion enemiga, team enemigo, o monstruo vanilla hostil |
| `ALLIES` | dueno, companeros de team, faccion aliada y mobs propios/aliados |

Los valores viejos siguen valiendo como objetivos especiales:

| `target` viejo | Quien es |
|---|---|
| `self` | el propio mob |
| `owner` | su dueno |
| `target` | a quien esta atacando ahora |
| `enemies` | los enemigos declarados **y** quien lo agredio (venganza), en radio |
| `allies` | companeros de team y aliados, en radio |

**Los objetivos usan la misma politica de combate que la IA**: si el mob considera
enemigo a alguien, sus skills tambien. No hay dos definiciones de "enemigo".

### Objetivos en mobs de servidor

Los mobs de `category: server` **no tienen dueno**, y eso cambia los objetivos:

- `owner` **nunca resuelve**: sin dueno, la skill no hace nada. No la uses ahi.
- `enemies` es faccion enemiga, o quien lo agredio; contra jugadores manda `attitude`.
- `allies` alcanza **solo a las facciones declaradas aliadas** en `factions.yml`. Ojo: la
  **misma faccion no cuenta como aliada** aqui, aunque en combate si es intocable.
- `target` funciona igual, pero necesita que el mob este peleando.

## Skills reutilizables (`skills/`)

Ademas de las skills dentro de un mob, puedes tener un archivo por skill en
`plugins/CustomCore/skills/`. El **id es el nombre del archivo** (sin `.yml`). Las skills
compartidas se referencian desde un mob con `ref:`:

```yaml
skills:
  - ref: grito_orco
```

Un archivo de `skills/`:

```yaml
mode: item
effect: IGNITE
target-type: LOOK
target: ENEMIES
range: 30
amount: 5
```

Las skills con `mode: item` solo las dispara el uso de un objeto; el planificador de mobs
las ignora aunque un mob las referencie.

### Teletransporte detrás

```yaml
- id: emboscada
  effect: teleport_behind
  target-type: LOOK
  target: ENEMIES
  distance: 1.0
  range: 20
```

```yaml
- id: escapar
  effect: backstep
  target-type: RADIUS
  target: SELF
  distance: 8
```

Busca un punto libre detrás del objetivo y deja al lanzador mirándolo. Con
`teleports.respect-protections: true` consulta las protecciones; por defecto es `false`.

## Detalles que importan

- En `effect: message` el `target` **se ignora**: el mob habla a los jugadores dentro
  de `range`. `{mob}` se sustituye por su nombre.
- `message` tambien funciona en `damage`, `heal` y `potion`: si el objetivo es un
  jugador, le llega el aviso. Es la forma de que una skill sea **observable**.
- Si falla la tirada de `chance`, se reintenta en el siguiente ciclo **sin gastar** el
  cooldown.
- `range` tiene un tope duro de **128 bloques**; lo que pidas por encima se recorta.
- `range` es **alcance maximo tambien para `owner` y `target`**: una skill de dueno no
  alcanza a su dueno si esta fuera del radio, ni una de objetivo a un enemigo lejano. El
  valor por defecto son **8 bloques**.
- Una skill mal escrita **se descarta sola**. Nunca tumba la carga del mob.
- El intervalo de evaluacion es `skills.interval-ticks` del config (20 = 1 segundo).
