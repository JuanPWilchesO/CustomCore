# Tutorial 9 — Pociones crafteables

Hasta aqui las pociones se repartian con `/customcore potion give`. En este tutorial se
craftean, se lanzan y se convierten en una herramienta de apoyo para el heal.

## 1. El efecto

Todo empieza en `effects.yml`. Un efecto se define una vez:

```yaml
purga:
  nombre: '&aPurga'
  descripcion: 'Quita los efectos negativos.'
  duracion: 1
  mecanica: quitar_negativos
  tipo: neutro
```

## 2. La poción bebible

En `potions.yml`:

```yaml
purga_bebible:
  nombre: '&aPurga'
  material: POTION
  color: GREEN
  efecto: purga
  receta:
    forma: [" A ", "ABA", " A "]
    ingredientes: { A: SUGAR, B: GLASS_BOTTLE }
    cantidad: 1
```

## 3. La arrojadiza

Mismo efecto, otro material. `SPLASH_POTION` se lanza; `LINGERING_POTION` deja una nube:

```yaml
bomba_purga:
  nombre: '&aBomba de purga'
  material: SPLASH_POTION
  color: GREEN
  efecto: purga
  radio: 4
  bando: aliados
  receta:
    forma:
      - " A "
      - "ABA"
      - " A "
    ingredientes:
      A: GUNPOWDER
      B: GLASS_BOTTLE
    cantidad: 1
```

`bando` decide a quien alcanza: `aliados`, `enemigos` o `todos`. Si no lo pones, sale del
`tipo` del efecto: positivo → aliados, negativo → enemigos, neutro → todos.

## 4. La forma de la receta

La receta es la misma que la de un objeto del catalogo: `forma` (el orden importa) o
`ingredientes` como lista (sin forma).

La forma de arriba es una cruz:

```text
  .  A  .
  A  B  A
  .  A  .
```

`A` va en **arriba, abajo, izquierda y derecha**; `B` en el **centro**. La botella tiene que
ser **vacía** (`GLASS_BOTTLE`), no una botella de agua. Se craftea en mesa 3x3.

## 5. Probar

```text
/cm reload
/cm potion give bomba_purga
```

Si la receta no se puede leer, el arranque lo avisa en consola con el id de la poción. Si
acabas de recargar y la receta no aparece en el cliente, reconecta: las recetas registradas
en caliente se sincronizan al entrar.

## 6. Errores comunes

- **No aparece el resultado**: revisa que la botella sea la vacía y que la pólvora este en
  cruz, no en las esquinas.
- **Cura a quien no debe**: falta `bando`, o el `tipo` del efecto no es el que crees.
- **No pasa nada al impactar**: el material no es `SPLASH_POTION` ni `LINGERING_POTION`.
- **El efecto dura menos de lo esperado**: con `duracion: 0` manda la duracion del efecto.
