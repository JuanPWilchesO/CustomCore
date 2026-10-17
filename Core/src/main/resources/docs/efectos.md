# Efectos y pociones

Un **efecto** se define una vez en `effects.yml` y se usa en tres sitios: una poción, una skill
de mob o el uso de un objeto del catálogo.

## El catálogo

| id | Qué hace |
|---|---|
| `congelamiento` | No sufres daño por congelación, el hielo no te agarra y se te quita la lentitud del frío. |
| `caida` | No sufres daño al caer. |
| `robo_vida` | Te curas un 25% del daño que haces (en cada golpe, no al matar). |
| `sigilo` | Desapareces para los demás, armadura incluida, y te vuelves invisible (F5). |
| `paso_helado` | Congelas el agua bajo tus pies y por delante. Se descongela a los 5 s. |

En el juego: `/customcore effects list` y `/customcore effects info <id>`.

## Definir un efecto (`effects.yml`)

```yaml
congelamiento:
  nombre: 'Inmunidad al congelamiento'
  descripcion: 'No sufres dano por congelacion y el hielo no te agarra.'
  duracion: 300          # segundos por defecto
  mecanica: congelamiento
  # vanilla: [SLOW_FALL, 'SPEED:2']   # efectos de Minecraft que aplica ademas
```

`mecanica` es lo que hace CustomCore: `congelamiento`, `caida`, `robo_vida`, `sigilo` o
`paso_helado`. `potencia` la usan las mecánicas que la necesitan (robo de vida).

## 1. Como poción (`potions.yml`)

```yaml
sangre_fria:
  nombre: '&bSangre Fria'
  material: POTION
  color: AQUA
  efecto: congelamiento
  duracion: 300
  faccion: atherea        # opcional: solo la usa esa faccion
  receta:                 # opcional: se craftea
    forma: [" A ", "ABA", " A "]
    ingredientes: { A: DIAMOND, B: GLASS_BOTTLE }
    cantidad: 1
```

Se reparte con `/customcore potion give <id> [jugador]`. Al beberse aplica el efecto. La receta,
si la pones, entra en el sistema de recetas normal (materiales o **objetos del catálogo**).

## 2. Como skill de un mob

```yaml
skills:
  - id: sed_de_sangre
    effect: potion
    target: self
    potion: robo_vida              # el id del efecto
    potion-duration-seconds: 120
    cooldown-seconds: 100          # se renueva antes de caducar
```

El campo `potion` acepta un efecto de Minecraft (`POISON`) o un **id de `effects.yml`**: si
coincide con un efecto del plugin, se aplica ese. Con `cooldown-seconds` menor que la duración,
el buff del mob no se cae nunca.

## 3. Como uso de un objeto (`items/<nombre>.yml`)

```yaml
usar:
  efecto: caida
  duracion: 120
  consume: false      # true = gasta una unidad
  cooldown: 60        # segundos por jugador
```

- Si el objeto es **comida** → se aplica al comer (el juego lo consume).
- Si no → **clic derecho** → se aplica y, con `consume: true`, se gasta una unidad.
- La **facción** del objeto se respeta igual que en el resto del catálogo.

## Aviso

Los efectos custom **no** son pociones de Minecraft: no aparecen en el yunque, ni en la mesa de
brebaje, ni los reconoce el libro de recetas salvo que les pongas `receta`. Se reparten con el
comando o con una receta.
