# Tutorial 4 — Mob de faccion con spawner

Los mobs de `category: server` no tienen dueno: pertenecen a una faccion y se gobiernan por
`attitude`. Este tutorial crea un orco que pelea contra las facciones enemigas y reaparece.

## 1. Declara la faccion

En `factions.yml`:

```yaml
orcos:
  allies: [goblins]
  enemies: [humanos]
humanos:
  enemies: [orcos, goblins]
```

Las relaciones son **simetricas**: si `orcos` declara `humanos` enemigo, vale al reves.

## 2. El mob

`mobs/orco.yml`:

```yaml
id: orco
category: server
display-name: '&2Orco'
type: ZOMBIE
faction: orcos
attitude: hostile

burn-in-daylight: false

attributes:
  health: 30.0
  damage: 6.0

leash:
  max-distance: 10.0
  teleport-distance: 24.0

respawn-seconds: 30
```

- **Sin `egg` ni `recipe`**: no se craftea. Se invoca con `/customcore spawn`.
- **Sin `anchor`**: los mobs de servidor quedan siempre anclados a su punto.
- `attitude: hostile` — ataca jugadores a la vista. Otras opciones: `neutral`, `defender`.
- `respawn-seconds: 30` — al morir, reaparece en el mismo bloque a los 30 s.

## 3. Invocalo

```
/customcore spawn orco
```

Desde el juego lo coloca donde estas; desde consola, `/customcore spawn orco <mundo> <x> <y> <z>`.

## 4. Comprueba el spawner

- Matelo (`/customcore kill orco` o a golpes).
- Mira `/customcore active`: desaparece.
- Espera 30 s: vuelve a estar, en el mismo sitio.
- El estado vive en `data/spawners.yml`, asi que **sobrevive a un reinicio**.

## 5. Peleas entre facciones

Coloca un mob de `humanos` cerca: se atacaran solos. Los de la **misma faccion o aliadas**
son intocables. Ver `../docs/facciones-equipos-y-combate.md`.

## 6. Faccion para un jugador

Un administrador puede meter a un jugador en una faccion:

```
/customcore faction set <jugador> <faccion>
```

A partir de ahi, los mobs de faccion lo tratan por `factions.yml`: **misma faccion o aliada =
intocable** (y sin fuego amigo), **enemiga = objetivo**. Sin faccion, manda la `attitude` del
mob, como siempre. `faction get`, `faction clear` y `faction list` completan el manejo, y todo
es de administracion.

## 7. Dos opciones utiles del mob

```yaml
# No molesta al pueblo: no apunta a aldeanos ni golems, y los golems no lo apuntan a el.
village-friendly: true

# Causas de dano que ignora. Vale en cualquier categoria.
immune:
  - fall
  - potion
```

Ojo con `village-friendly`: que el mob no ataque ni sea atacado se arregla con la clave, pero
que los aldeanos **no huyan** depende del **tipo de entidad** —huyen de zombis e illagers, no
de esqueletos—. Para convivir con aldeanos, usa la familia **esqueleto**.

Los detalles de ambas, en `../docs/el-archivo-de-un-mob.md`.
