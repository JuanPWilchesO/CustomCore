# Tutorial 6 — Disfraz, sonidos y colores

## 1. Disfraz (LibsDisguises)

Un mob puede verse como un jugador con la skin que elijas:

```yaml
disguise:
  enabled: true
  type: player
  skin: NombreDeJugador
  show-name: true
```

- `type: player` usa `skin` (nombre de un jugador cuya skin copiar) o `skin-url`.
- `show-name: true` mantiene visible el nombre del mob.
- Sin **LibsDisguises** instalado, la seccion se ignora y el mob conserva su apariencia
  normal: el plugin degrada solo.

El disfraz se aplica **antes** de crear la entidad, asi que el mob nunca se ve un instante
con su forma base.

## 1b. Modelo 3D (BetterModel)

La otra forma de darle cuerpo a un mob: un modelo de **BetterModel** encima de la entidad.

```yaml
model:
  enabled: true
  name: demon_knight
  hide-body: true
  hide-equipment: true
  animations:
    idle: idle
    walk: walk
    attack: { name: hammer_attack_1, once: true }
    death:  { name: death, once: true }
```

- `name` es el del modelo en `plugins/BetterModel/models/`. Los ves con
  `/customcore model list`.
- `hide-body` oculta el cuerpo vanilla: solo se ve el modelo.
- `animations` mapea eventos del mob (`idle`, `walk`, `attack`, `hurt`, `death`, `spawn`)
  a animaciones **del modelo**. Un evento sin clave no anima nada.
- El modelo necesita un hueso con el tag `hitbox` (o `seat`) en Blockbench. Sin hitbox se
  deja el cuerpo vanilla visible, para que el mob siga recibiendo dano.

**Disfraz y modelo no se mezclan**: si el mob tiene las dos cosas, gana el disfraz y el
modelo se ignora (el log lo avisa una vez).

Sin BetterModel instalado, la seccion se ignora sin errores.

## 2. Sonidos

```yaml
sounds:
  ambient:
    sound: entity.zombie.ambient
    volume: 1.0
    pitch: 0.7
    interval-seconds: 10
  hurt:   { sound: entity.zombie.hurt,  volume: 1.0, pitch: 0.7 }
  death:  { sound: entity.zombie.death, volume: 1.0, pitch: 0.7 }
  attack: { sound: entity.zombie.attack, volume: 1.0, pitch: 0.7 }
```

- `ambient` se repite cada `interval-seconds`.
- Los ids de sonido son los de Minecraft (`entity.zombie.hurt`, `block.anvil.land`…).
- Si configuras **al menos uno**, el mob deja de sonar por su cuenta: se oye **solo** lo del
  yml. El costo es que pierde su sonido de pasos y el de su muerte vanilla.
- Se oyen a **32 bloques**; mas lejos, no.
- Un mob sin `sounds:` suena como cualquier bicho vanilla.

## 3. Color y brillo de tus mobs

Cada jugador distingue los suyos:

```
/customcore color <color|nada>     color del nombre
/customcore glow <color|nada>      brillo, y de que color
```

- El color **tine el nombre entero** y sobreescribe el del yml.
- El brillo **se hereda del yml** del mob. Si el yml no lo tiene, se queda sin glow.
  `nada` lo apaga explicitamente.
- **Con Teams, el ajuste es del team**: lo decide solo su jefe y aplica a los mobs de todos
  los miembros. Sin Teams, cada jugador manda sobre los suyos.

Se guarda en `styles.yml` y se aplica en caliente: cambias el color y tus mobs vivos se
actualizan al momento.

## 4. Combinacion tipica

```yaml
id: capitan
category: player
display-name: '&4Capitan'
type: ZOMBIE
egg: ZOMBIE_SPAWN_EGG
glow: true
disguise:
  enabled: true
  type: player
  skin: Notch
attributes:
  health: 80.0
  damage: 9.0
sounds:
  ambient: { sound: entity.wither.ambient, volume: 0.8, pitch: 0.6, interval-seconds: 12 }
```

Recarga, invocalo y ajusta su color con `/customcore color` para reconocerlo entre los
demas.
