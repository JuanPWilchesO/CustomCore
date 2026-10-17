# El libro de inspeccion y los items de mejora

Dos herramientas que giran alrededor de lo mismo: que un jugador pueda **ver y mejorar sus
propios mobs**.

---

## Libro de inspeccion

Un item tipo libro que muestra al jugador **sus mobs con sus estadisticas de combate**.

- **Solo lo reparte un administrador**: `/customcore book [jugador]` (alias `libro`).
- **Cualquier jugador puede usarlo**. El permiso es para *repartirlo*, no para usarlo: asi
  el servidor lo puede vender, regalar o incluir en su sistema de economia.
- Al usarlo (clic derecho, con el libro en la mano) se abre **un libro de verdad** con
  **una pagina por mob**.

Por cada mob **desplegado** muestra:

| Dato | Que es |
|---|---|
| Nombre | el `display-name` del mob |
| Id | la definicion, para saber de cual se trata |
| Vida | la **actual** y la **maxima** (con las mejoras ya aplicadas) |
| Ataque | el dano de ataque |
| Velocidad | la velocidad de movimiento |
| Armadura | la armadura |
| Ubicacion | mundo y coordenadas, para encontrarlo |

Los mobs **guardados en su huevo** tambien aparecen, con la vida que llevaban guardada.

### Configurarlo (`config.yml`)

```yaml
book:
  material: WRITTEN_BOOK
  display-name: '&6Inventario de fuerzas'
  lore:
    - '&7Muestra tus mobs y sus estadisticas.'
    - '&7Clic derecho para abrirlo.'
```

> **Ojo con el color**: el libro se pinta sobre el **papel claro**, asi que los tonos
> blancos y muy claros no se leen. Usa colores **oscuros** para el texto y las etiquetas.

---

## Items de mejora

Items que **suben caracteristicas de combate** de un mob del jugador. Se definen en
archivos, uno por item, en `upgrades/`:

```yaml
id: piedra_vida
material: AMETHYST_SHARD
display-name: '&dPiedra de vida'
lore:
  - '&7Se la das a un mob tuyo'
  - '&7para subirle la vida maxima.'

# Caracteristica -> CUANTO SUBE (no el valor final)
stats:
  health: 20.0

recipe:
  # 0 = solo se reparte con el comando. 1 o mas = se craftea.
  amount: 0
  # Ingredientes extra, ademas del material base. La receta es SIN FORMA.
  extras:
    - DIAMOND
    - DIAMOND
```

### Que se puede mejorar

Solo caracteristicas de **combate**. Si un archivo pide otra, esa clave se descarta con un
aviso en el log y el item carga igual:

`health`, `damage`, `speed`, `armor`, `armor-toughness`,
`knockback-resistance`, `attack-speed`, `attack-knockback`.

El valor es **cuanto sube**, y se suma a lo que el mob ya tenia: dos piedras de vida aplican
40, no 20. En `health` hay un detalle: al subir el maximo tambien se cura al mob en la misma
cantidad, para que la mejora se note en el acto.

### Como se aplica

El jugador sostiene el item y hace **clic derecho sobre un mob suyo**. El item **se
consume** y se le avisa de lo que subio. Si el mob no es suyo, no pasa nada y **no se gasta**.

### Craftearlo o no

- `recipe.amount: 0` → **no se craftea**: solo se reparte con
  `/customcore upgrade give <id> [jugador]`.
- `recipe.amount: 1` o mas → se craftea sin forma, con el material base y los `extras`.

Dos items con **los mismos ingredientes** son indistinguibles para el cliente, y el segundo
se omite con un aviso — vale tambien contra las recetas de los huevos.

### Las mejoras viajan con el mob

Esto es lo importante: la mejora modifica los **atributos base** del mob, y el huevo guarda
las **desviaciones** respecto a su definicion. Por eso, si el jugador **recoge el mob y lo
vuelve a colocar, la mejora sigue ahi**. Lo que la definicion fije y nadie haya mejorado se
sigue leyendo del yml.

### Comandos

```
/customcore upgrade list                     que items de mejora hay
/customcore upgrade give <id> [jugador]      reparte uno
/customcore upgrade reload                   relee upgrades/ y rehace las recetas
```

`mejora` funciona como alias de `upgrade`.

---

## Ver tambien

- `el-archivo-de-un-mob.md` — las claves de `mobs/*.yml`, incluidas las de `attributes`.
- `comandos-permisos-y-cupos.md` — el resto de comandos y los permisos.
