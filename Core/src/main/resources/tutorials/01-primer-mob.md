# Tutorial 1 — Tu primer mob

Vamos a crear un guardia que sigue a su dueno y lo protege. Diez minutos.

## 1. Crea el archivo

`plugins/CustomCore/mobs/guardia.yml`:

```yaml
id: guardia
category: player
display-name: '&bGuardia'
type: ZOMBIE
egg: ZOMBIE_SPAWN_EGG

attributes:
  health: 40.0
  damage: 5.0

leash:
  anchor: owner
  max-distance: 10.0
  teleport-distance: 24.0
```

## 2. Cargalo

```
/customcore reload
```

Debe aparecer `guardia` en `/customcore list`.

## 3. Consigue el huevo

Dos caminos:

- `/customcore give guardia` — te lo da directamente.
- Craftealo: **huevo de zombi + diamante** (la receta por defecto, porque no pusimos
  `recipe.extras`).

## 4. Invocalo

Mira a un bloque del suelo y **clic derecho con el huevo**. Nace el Guardia, **tu eres su
dueno**, y el huevo queda **ligado** a el (no se gasta).

A partir de ahi:

- **Golpealo con el huevo** para recogerlo (vuelve dentro del huevo).
- **Vuelve a colocar** el huevo para desplegarlo.
- Si **muere**, el huevo queda inerte.

## 5. Comprueba

- `/customcore active` — debe listarlo con `[dueno: TuNombre]`.
- Alejate: a mas de 10 bloques intenta volver; a mas de 24 se teletransporta.

## Que has aprendido

Categoria `player`, huevo, atributos, ancla al dueno y el ciclo colocar/recoger. El
siguiente tutorial anade equipo y encantamientos.
