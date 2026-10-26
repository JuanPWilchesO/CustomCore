# 08 — Monturas

Una montura es un mob que el jugador **monta**. Se declara con `category: mount` y su
entidad tiene que ser un caballo o similar.

## 1. El mob minimo

```yaml
id: mi_montura
category: mount
display-name: '&7Mi montura'
type: HORSE
egg: HORSE_SPAWN_EGG

attributes:
  health: 40.0
  speed: 0.3
  jump-strength: 0.8

mount:
  saddled: true
  tamed: true

recipe:
  extras:
    - GOLD_INGOT
  amount: 0
```

`tamed: true` y `saddled: true` son lo que te deja subirte. Sin ellos, no hay forma.

## 2. Darle aspecto

```yaml
mount:
  color: BLACK
  style: WHITE_DOTS
  armor: DIAMOND_HORSE_ARMOR
```

`color` y `style` solo existen en caballos. `armor` vale para cualquier montura que la
admita.

### Montura con modelo 3D

Si el modelo trae un hueso marcado como `seat` en Blockbench, el caballo vanilla se oculta
y el jugador se sube al asiento del modelo:

```yaml
category: mount
type: HORSE
model:
  name: caballo_guerra
  seat: true
  animations:
    idle: idle
    walk: walk
```

- `seat: true` (por defecto si declaras `model`) usa el hueso `seat` como asiento.
- Sin hueso `seat`, el jugador se monta al asiento vanilla y el log avisa.
- `mount.saddled` y `mount.tamed` siguen haciendo falta: el mob tiene que dejarse montar.

Necesita el plugin **BetterModel**. Sin el, la montura se ve y se comporta como siempre.

## 3. Efectos

```yaml
mount:
  effects:
    - potion: fire_resistance          # al propio caballo
    - potion: conduit_power
      to: rider                        # a quien la monta
    - potion: speed
      to: nearby
      radius: 8.0                      # a tu bando dentro de 8 bloques
```

Los efectos van **sin particulas ni icono** por defecto. Para verlos mientras pruebas,
ponles `visible: true`.

## 4. Paso helado

```yaml
mount:
  frost-walker: true
```

Subete y camina sobre el agua: se hiela bajo las patas y se derrite al cabo de un rato.

> **Ojo**: el paso helado es una funcion **experimental**. Depende de que el servidor vaya
> fino; con lag o TPS bajo el hielo llega tarde y la montura se cae al agua. Los detalles,
> en `../docs/monturas.md`.

## 5. El cupo es aparte

Las monturas tienen su propia cuenta. Con el config por defecto, **una montura por
jugador**:

```yaml
limits:
  default-mount-mobs: 1
  mount-groups:
    default: 1
    vip: 2
    staff: 3
```

`/customcore cuota` muestra las tres lineas: seguidores, fijos y monturas.

## 6. No se teletransporta

A diferencia de un mob que te sigue, **una montura no aparece sola a tu lado**. Si la dejas
atras, se queda donde esta. Si no vuelves a por ella, la **ventana de abandono** se la
lleva, con un aviso por chat y su plazo.

## 7. Probarlo

1. `/customcore give mi_montura`, coloca el huevo y subete.
2. Comprueba color, marcas y armadura.
3. Camina sobre el agua: debe helarse.
4. `/customcore cuota` → tu montura debe estar en la linea de **Monturas**.
5. Golpeala con su huevo para recogerla y volver a colocarla.

Sigue con `../docs/monturas.md` para la referencia completa.
