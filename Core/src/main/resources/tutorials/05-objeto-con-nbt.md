# Tutorial 5 — Un objeto con NBT

Algunos objetos no se pueden escribir a mano en un yml: un encantamiento de otro plugin,
cualquier NBT puesto por un plugin de items. Para eso esta el **catalogo**.

## 1. Guarda el objeto

Sostien en la mano el objeto ya preparado (con su encantamiento, su nombre, su NBT) y
ejecuta:

```
/customcore item save espada_del_sargento
```

El nombre se normaliza a minusculas y sin espacios. Se crea
`plugins/CustomCore/items/espada_del_sargento.yml`:

```yaml
nombre: espada_del_sargento
item: '<base64 con todo el NBT>'
etiqueta: '&cEspada del Sargento'
```

## 2. Usalo en el equipo de un mob

```yaml
equipment:
  main-hand:
    item: espada_del_sargento
    drop-chance: 0.0
```

Con `item:` el objeto manda **tal cual** y conserva su NBT.

## 3. O como drop

```yaml
drops:
  - item: espada_del_sargento
    chance: 0.25
    min: 1
    max: 1
```

## 4. Gestiona el catalogo

```
/customcore item list
/customcore item remove espada_del_sargento
```

Un **mismo objeto sirve para varios mobs**. Si un drop nombra un objeto que no existe, se
avisa **una vez** en el log y ese drop se omite: no rompe nada.

## 5. Uso del objeto con skills

Un objeto tambien puede lanzar **skills** en vez de un efecto suelto:

```yaml
clase: mago
usar:
  skills:
    - balsamo_propio
    - balsamo_aliados
  consume: false
  cooldown: 30
  inicio: '&bBálsamo listo.'
```

- `usar.skills` es una lista, en orden; `usar.skill` sirve para una sola.
- `usar.efecto` sigue valiendo para un efecto de `effects.yml`.
- El cooldown efectivo es el **mayor** entre el del item y el de las skills.
- La `clase` y la `faccion` se comprueban antes de lanzar la skill.
- `console_command`, `player_command` y `chain` exigen `mode: item`; un mob no las
  ejecuta aunque las referencie.
- `usar.inicio` es del item; el cooldown es visual. El `fin` de un item con
  `usar.skills` no se usa: pon el `fin` en cada skill.
- `{jugador}` se sustituye por el nombre de quien usa el objeto.
- Si una skill aplica `potion: sigilo`, `reveal-on-attack: true` va en la **skill**, no en
  el item. Se explica en `03-skills.md`.

Las skills compartidas viven en `plugins/CustomCore/skills/<id>.yml` y se referencian
por su id. Para probar sin depender del clic: `/customcore testcast <skill>`.

## 6. Un arco que cura al impactar

Una skill `mode: impacto` no se lanza al usar el objeto: se dispara cuando el proyectil toca.

```yaml
# skills/flecha_cura.yml   (el id es el nombre del archivo)
mode: impacto
effect: heal
target-type: look
target: impacto
amount: 6
cooldown-seconds: 8
```

```yaml
# items/arco_sanador.yml
material: BOW
usar:
  skills: [flecha_cura]
  cooldown: 8
  inicio: '&aFlecha cargada.'
```

Con `target: impacto` cura a quien golpee, si es aliado. Para un area, `target-type: radius`
con `target: allies` y `range`. La municion tambien puede llevar las skills: una flecha
custom funciona con cualquier arco.

Lo mismo vale para un **tridente** o una **bola de nieve** custom: se arman al lanzarlos y
resuelven al tocar. El tridente se lanza siempre, aunque su cooldown este activo; en ese
caso el lanzamiento es normal y solo se salta el efecto. `consume` no aplica a proyectiles:
la flecha y la bola de nieve se gastan al lanzarse y el tridente no se destruye.
