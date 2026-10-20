# Drops y catalogo de objetos

## Tabla de drops

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

- `amount` es un atajo de `min`/`max`.
- `chance` va de `0.0` (nunca) a `1.0` (siempre).
- Un nombre que no exista en el catalogo no rompe nada: se avisa **una vez** en el log,
  nombrando el mob, y ese drop se omite.

## Catalogo de objetos con NBT

Hay objetos que **no se pueden escribir a mano** en un yml: un encantamiento de
ExcellentEnchants, cualquier NBT puesto por otro plugin. Para esos esta el catalogo.

```
/customcore item save <nombre>     guarda el objeto de tu mano
/customcore item list              lista los nombres guardados
/customcore item remove <nombre>   borra uno
/customcore item give <nombre> [jugador]   entrega una copia nueva
/customcore item faccion <nombre> <faccion|nada>   lo reserva a una faccion (o lo libera)
```

Cada objeto vive en **`items/<nombre>.yml`**, con su nombre escrito dentro:

```yaml
nombre: espada_del_sargento
item: '<base64 con todo el NBT>'
etiqueta: '&cEspada del Sargento'
faccion: rojos   # opcional: solo los de esa faccion pueden recogerlo, equiparlo o usarlo
```

El nombre se normaliza a minusculas y sin espacios, porque acaba siendo un nombre de
archivo. Despues lo llamas por ese nombre desde la tabla de drops o desde el
equipamiento. **Un mismo objeto sirve para varios mobs.**

> Un objeto del catalogo **no vale como ingrediente** de una receta que no sea del plugin: si lo
> pones en la mesa y el resultado no es de CustomCore, el crafteo se cancela. Asi un objeto con
> NBT no acaba de relleno en un crafteo vanilla.

### Reservar un objeto a una faccion

```
/customcore item faccion espada_del_sargento rojos   solo los 'rojos' pueden usarla
/customcore item faccion espada_del_sargento nada    la libera: vale para cualquiera
```

Un objeto reservado solo lo puede **recoger del suelo**, **sacar de un cofre**, **mover en su
inventario**, **equipar**, **usar** o **craftear** un jugador de esa faccion. La marca viaja **dentro del objeto** (su NBT), asi que sigue puesta si lo sueltan, lo
guardan en un cofre o lo reparte otro plugin — por eso no basta con la clave en el yml: hay
que ponersela con el comando (o escribirla a mano antes de usarlo). El que no sea de la
faccion, al intentarlo, recibe el aviso y no pasa nada mas.

Si un jugador de otra faccion tiene el objeto en su inventario (por ejemplo, lo recibio en un
mundo donde el bloqueo no aplica) y **hace clic en el**, el objeto **se cae al suelo** y el slot
queda libre — no se le queda preso. En un cofre ajeno, en cambio, solo se cancela el clic: el
objeto se queda donde estaba.

El aviso de "no es la tuya" tiene un **enfriamiento de 3 segundos**: a clics seguidos no
inunda el chat.

### Reservar un objeto a una clase

```yaml
clase: guerrero
```

O con el comando, igual que la faccion:

```
/customcore item clase <nombre> guerrero   lo reserva a esa clase
/customcore item clase <nombre> nada       lo libera
```

El objeto queda reservado a esa **clase** (`/customcore class set <jugador> <clase>`). La clase no
tiene relaciones de alianza: solo restringe objetos.

Si el objeto declara **las dos**, `faccion:` y `clase:`, solo lo usa quien sea de **esa faccion y
esa clase**. Por ejemplo, un objeto de `atherea` y `guerrero` no lo usa nadie mas: ni un atherea
mago, ni un guerrero de otra faccion. Eso permite montar escenarios clase-faccion.

Un **admin** (quien tenga el permiso de administracion) pasa siempre: reservar un objeto no
puede dejarle sin poder revisarlo ni moverlo. Como un op tiene todos los permisos, cae ahi solo.

> No es una caja fuerte: impide **usarlo**, no quitarselo a quien ya lo tenga. Un objeto ya
> equipado por alguien de otra faccion no se le arranca.

### Uso con skills

Un objeto del catalogo puede lanzar **skills** en vez de un efecto suelto. En su yml:

```yaml
clase: mago
usar:
  skills:
    - balsamo_propio
    - balsamo_aliados
  consume: false
  cooldown: 30
```

- `usar.skills` es una lista, en orden; `usar.skill` sirve para una sola.
- `usar.efecto` sigue existiendo para un efecto de `effects.yml`. Si un item define
  `usar.skills` y `usar.efecto`, se ejecutan las skills y el efecto se ignora.
- El **cooldown efectivo** es el mayor entre `usar.cooldown` y el
  `cooldown-seconds` de las skills. No se suman.
- `consume` decide si se gasta una unidad.
- `usar.inicio` y `usar.fin`: avisos en el chat del jugador al usarlo y al caducar
  el efecto. `usar.fin` solo aplica cuando el item usa `usar.efecto`; con
  `usar.skills`, el fin lo pone el `fin` de cada skill.
- `usar.cooldown-aviso`: aviso al intentar usarlo en espera; admite `{segundos}`,
  `{tiempo}` o `{cooldown}`. Si falta, se usa el `cooldown-aviso` de la primera skill.
- Los avisos van al jugador que usa el objeto, no al objetivo.
- Si el objeto tiene `clase` y/o `faccion`, la restriccion se comprueba **antes** de
  lanzar la skill. Un admin pasa solo si tiene `customcore.bypass`, no por ser admin.
- Por defecto CustomCore procesa igual el uso aunque otro plugin cancele el clic
  (`items.ignore-cancelled-use: true`), pero sigue respetando clase/faccion. Ponlo en
  `false` si quieres que la cancelacion del otro plugin mande.
- Las skills con `console_command` o `player_command` deben declarar `mode: item`;
  el planificador de mobs no las ejecuta. `chain` no se admite desde objetos: solo
  desde `mode: interaccion`.

Un ejemplo completo con avisos:

```yaml
usar:
  skill: invul_paladin
  consume: false
  cooldown: 120
  inicio: '&bCristal activado: 10 s de invulnerabilidad.'
  cooldown-aviso: '&cEspera {segundos}s para volver a usarlo.'
```

Para un efecto directo, donde sí vale `usar.fin`:

```yaml
usar:
  efecto: caida
  duracion: 120
  cooldown: 60
  inicio: '&aCaída segura activada.'
  fin: '&7La caída segura terminó.'
  cooldown-aviso: '&cEspera {segundos}s.'
```

### Skills al impactar

Un objeto puede llevar skills `mode: impacto`. En vez de ejecutarse al usarlo, se arman
cuando sale el proyectil y se resuelven cuando toca:

```yaml
# items/flecha_curandera.yml
material: ARROW
usar:
  skills: [flecha_cura]
  cooldown: 8
  inicio: '&aLa flecha brilla.'
```

Vale en el **arma** (arco o ballesta) o en la **municion**. Si la municion trae skills, mandan
las de la municion; si no, las del arma. Una flecha custom sirve con cualquier arco.

Si todas las skills del objeto son de impacto, el clic derecho no dispara nada: el objeto no
se "usa", se dispara. `consume` tampoco aplica, porque el juego ya gasta la flecha, la bola
de nieve o el tridente.

Cubiertos: flecha, ballesta, municion, tridente, bola de nieve y huevo.

### Flechas cargadas

Cuando el ranger tiene varias flechas custom no hace falta tenerlas en la mano: el arco
dispara la que este **cargada**.

1. Con la flecha en la mano, **clic derecho**: queda cargada. El chat confirma
   `Flecha cargada: Hielo (12)`.
2. Cambia al arco, con el totem en la otra mano, y dispara. Cada disparo gasta una flecha de
   ese tipo.
3. Para cambiar, clic derecho con otra flecha.

La carga es **por jugador**: sirve con cualquier arco y sobrevive a reconexiones y
reinicios. Si no quedan flechas de ese tipo, el disparo se bloquea y el chat lo avisa; nunca
se gasta otro tipo por accidente. Con `Infinity` no se consume ninguna.

La flecha cargada manda sobre las skills del arco. Las del arco solo aplican cuando no hay
ninguna cargada.

### Modificaciones del objeto

Un objeto puede impedir que lo modifiquen. En su yml:

```yaml
modificable: false          # master: bloquea todo
encantar-libros: false      # yunque con libro encantado
encantar-mesa: false        # mesa de encantamientos
renombrar: false            # yunque
reparar: false              # yunque y Mending
desencantar: false          # grindstone
tallar: false               # mesa de herreria
```

Si una clave no aparece, esa modificacion esta **permitida**. `modificable: false`
bloquea todas. Por comando:

```
/customcore item mod <nombre> <clave> <si|no>
```

Claves: `modificable`, `encantar-libros`, `encantar-mesa`, `renombrar`, `reparar`,
`desencantar`, `tallar`. Las copias ya repartidas no cambian; las nuevas copias de
`item give` llevan la marca dentro del NBT.

## De la mano al mob, en dos pasos

1. Sostien el objeto con su NBT y ejecuta `/customcore item save mi_espada`.
2. Llamalo desde el mob:

```yaml
equipment:
  main-hand:
    item: mi_espada
```

o

```yaml
drops:
  - item: mi_espada
    chance: 0.3
```

## Recetas de un objeto del catalogo

Las pociones de `potions.yml` usan **el mismo esquema** de `receta:` que se explica aqui.

Un objeto guardado puede traer su **propia receta**, en el mismo `items/<nombre>.yml`. Un
ingrediente puede ser **otro objeto guardado** (por su nombre) o un **material** vanilla.

### Con forma: el orden importa

```yaml
receta:
  forma:            # la rejilla: hasta 3 filas de hasta 3 caracteres. El espacio es un hueco.
    - "ABA"
    - " C "
    - " C "
  ingredientes:     # letra -> objeto del catalogo (por su nombre) o material vanilla
    A: espada_anduin
    B: DIAMOND
    C: mango_runico
  cantidad: 1       # cuantos salen (1-8)
```

La misma pieza en otra casilla es **otra receta**: eso es lo que fija el orden.

### Sin forma: da igual donde

```yaml
receta:
  ingredientes: [espada_anduin, DIAMOND, DIAMOND]
  cantidad: 2
```

### Reglas y avisos

- El resultado **es el objeto de ese yml**, con su NBT y su faccion si la tiene. Si el objeto
  esta **reservado a una faccion**, solo lo pueden craftear los jugadores de esa faccion.
- Un ingrediente que sea **otro objeto del catalogo** se compara por su **NBT exacto**: no vale
  cualquier cuchillo del mismo material. Por eso conviene repartir copias con
  `/customcore item give`.
- Si un nombre no es ni objeto del catalogo ni material, o la rejilla no cuadra, la receta se
  **omite y avisa en el log** — no rompe el arranque.
- Dos recetas con la misma forma e ingredientes **chocan**: la segunda se omite con aviso.
- **Cuidado con las recetas vanilla**: un objeto del catalogo sigue siendo su material base (el
  hierro fantasma es un lingote), asi que una receta tuya que solape **exactamente** una vanilla
  —misma forma y mismo material— puede resolverse con la vanilla. Y entonces
  [el crafteo se cancela](#). Anade un ingrediente o cambia la forma para que no choquen.
- Los cambios no se aplican solos: hace falta **`/customcore reload`** (o reiniciar).
- `item list` marca los objetos que ya tienen receta: `[crafteable]`.
