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
/custommobs item save <nombre>     guarda el objeto de tu mano
/custommobs item list              lista los nombres guardados
/custommobs item remove <nombre>   borra uno
/custommobs item faccion <nombre> <faccion|nada>   lo reserva a una faccion (o lo libera)
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

### Reservar un objeto a una faccion

```
/custommobs item faccion espada_del_sargento rojos   solo los 'rojos' pueden usarla
/custommobs item faccion espada_del_sargento nada    la libera: vale para cualquiera
```

Un objeto reservado solo lo puede **recoger del suelo**, **sacar de un cofre**, **mover en su
inventario**, **equipar** o **usar** un jugador de esa faccion. La marca viaja **dentro del objeto** (su NBT), asi que sigue puesta si lo sueltan, lo
guardan en un cofre o lo reparte otro plugin — por eso no basta con la clave en el yml: hay
que ponersela con el comando (o escribirla a mano antes de usarlo). El que no sea de la
faccion, al intentarlo, recibe el aviso y no pasa nada mas.

> No es una caja fuerte: impide **usarlo**, no quitarselo a quien ya lo tenga. Un objeto ya
> equipado por alguien de otra faccion no se le arranca.

## De la mano al mob, en dos pasos

1. Sostien el objeto con su NBT y ejecuta `/custommobs item save mi_espada`.
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
