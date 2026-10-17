# Tutorial 7 — El libro de inspeccion y un item de mejora

Dos piezas pensadas para que el jugador cuide de sus mobs: **verlos** y **mejorarlos**.

## 1. El libro de inspeccion

Repartelo (hace falta ser administrador):

```
/customcore book
```

Te lo deja en el inventario. **Clic derecho** y se abre: una pagina por cada mob tuyo, con
su vida actual y maxima, su ataque, su velocidad, su armadura y donde esta. Los que tienes
guardados en un huevo tambien salen, con la vida que llevaban.

Lo importante: **el permiso es para repartirlo, no para usarlo**. Cualquier jugador que lo
tenga en la mano puede abrirlo, asi que tu servidor lo puede vender, regalar o meter en su
economia como quiera.

## 2. Un item de mejora

Crea `plugins/CustomCore/upgrades/piedra_vida.yml`:

```yaml
id: piedra_vida
material: AMETHYST_SHARD
display-name: '&dPiedra de vida'
lore:
  - '&7Se la das a un mob tuyo'
  - '&7para subirle la vida maxima.'
stats:
  health: 20.0
recipe:
  amount: 1
  extras:
    - DIAMOND
```

Recarga y repartetelo:

```
/customcore upgrade reload
/customcore upgrade give piedra_vida
```

Ahora tienes la piedra en la mano. **Clic derecho sobre uno de tus mobs** y le sube 20 de
vida maxima — y lo cura en la misma cantidad, para que se note en el acto. La piedra se
gasta.

## 3. Lo que hace especial a este item

Hiere al mob, **recogelo con su huevo y vuelve a colocarlo**. La mejora **sigue ahi**: la
vida maxima subida viaja con el mob.

Eso es lo que hace que valga la pena mejorarlos: lo que el jugador invierte en un mob no se
pierde porque lo guarde un rato.

## 4. Mas de un efecto a la vez

Un item puede subir varias cosas:

```yaml
stats:
  health: 20.0
  damage: 2.0
  armor: 4.0
```

Y se pueden repartir varios: dos piedras de vida aplicadas dan **40**, no 20 — el valor se
suma a lo que el mob ya tenia.

## 5. Crafteo

Con `recipe.amount: 1` o mas, se craftea **sin forma**: el material base mas los `extras`.
Con `amount: 0` no se craftea y solo se reparte con `upgrade give`.

> Ojo: dos items con los **mismos ingredientes** son indistinguibles para el cliente, y el
> segundo se omite con un aviso en el log. Vale tambien contra las recetas de los huevos.
