# Tutorial 2 — Equipo y encantamientos

Sobre el guardia del tutorial 1, vamos a armarlo y blindarlo.

## 1. El equipo

En `guardia.yml`, anade `equipment`:

```yaml
equipment:
  helmet:
    material: NETHERITE_HELMET
    unbreakable: true
    drop-chance: 0.0
    enchants:
      protection: 4
  main-hand:
    material: IRON_SWORD
    name: '&bFilo del guardia'
    unbreakable: true
    drop-chance: 0.0
    enchants:
      sharpness: 3
      fire_aspect: 2
```

- **Slots**: `helmet`, `chestplate`, `leggings`, `boots`, `main-hand`, `off-hand`
  (tambien valen `head`, `chest`, `CHEST`…).
- `drop-chance: 0.0` = **nunca lo suelta** al morir. `1.0` = siempre.
- `unbreakable: true` evita que se desgaste.

## 2. Encantamientos custom

Si tienes **ExcellentEnchants**, puedes usar sus encantamientos:

```yaml
enchants:
  excellentenchants:regrowth: 2
```

- Sin namespace (`sharpness`), se busca primero en vanilla y luego en ExcellentEnchants.
- Con namespace (`excellentenchants:regrowth`), se usa ese tal cual.
- Los ids no se validan al cargar: si escribes mal uno, el mob carga igual y el
  encantamiento simplemente no aparece.

Para saber los ids reales:

```
/customcore enchants excellentenchants
```

## 3. Recarga y prueba

```
/customcore reload
```

Invoca el guardia y miralo: debe traer casco y espada. Matelo y comprueba que **no suelta**
el equipo (porque `drop-chance` es 0).

## 4. Objetos con NBT

Si un objeto lleva NBT que no se puede escribir a mano, guardalo en el catalogo y
referencialo por nombre:

```
/customcore item save filo_del_guardia
```

```yaml
equipment:
  main-hand:
    item: filo_del_guardia
    drop-chance: 0.0
```

Ver `../docs/drops-y-objetos.md`.
