# CustomCore — documentacion

Mobs definidos por YAML, invocados con huevos crafteados, con facciones, disfraces,
equipamiento, encantamientos custom, skills y un cupo de mobs por jugador que sale del
grupo de LuckPerms.

Pensado para **Paper**. Se apoya en tres plugins opcionales: **Teams** (bandos de
jugadores), **LibsDisguises** (apariencias) y **LuckPerms** (grupos y cupos). Sin
cualquiera de ellos el plugin sigue funcionando: cada uno degrada por su cuenta.

---

## Requisitos

- **Paper 26.3** (Minecraft 26.3). Es la unica version probada. `api-version` vale
  26.3, asi que Paper rechaza versiones anteriores en vez de cargar el plugin y fallar
  despues.
- **Java 25**, el que exige Paper 26.3.
- Opcionales, cada uno independiente:
  - **LuckPerms** — para el cupo por grupo. Sin el, todos caen al cupo por defecto.
  - **Teams** — para bandos de jugadores. Sin el, cada jugador solo se protege a si mismo.
  - **LibsDisguises** — para dar apariencia a los mobs.

---

## Instalacion

1. Copia el jar a `plugins/`.
2. Arranca el servidor una vez. Se crean:
   - `plugins/CustomCore/config.yml` — configuracion general.
   - `plugins/CustomCore/factions.yml` — relaciones entre facciones.
   - `plugins/CustomCore/mobs/` — un archivo por mob. Se copian **seis ejemplos
     numerados** que funcionan como tutorial: abrelos en orden.
   - `plugins/CustomCore/docs/` — esta documentacion.
   - `plugins/CustomCore/tutorials/` — los tutoriales paso a paso.
3. Edita lo que quieras.
4. `/customcore reload` — recarga mobs, config y facciones **sin reiniciar**.

> `plugin.yml` es la excepcion: los permisos que declara solo se leen al arrancar. Si
> cambias sus valores por defecto hace falta reiniciar el servidor.

---

## Por donde empezar

1. **`tutorials/01-primer-mob.md`** — crea tu primer mob y invocalo.
2. **`el-archivo-de-un-mob.md`** — todas las claves que admite un mob.
3. **`skills.md`** — hechizos, curaciones, mensajes y pociones.
4. **`anclas-spawners-y-huevo.md`** — el huevo como ficha del mob, y los spawners.
5. **`monturas.md`** — caballos que se montan, con efectos y paso helado.
6. **`facciones-equipos-y-combate.md`** — a quien ataca cada mob, y por que.
7. **`comandos-permisos-y-cupos.md`** — todo lo que se puede escribir por chat.

## Indice completo

| Archivo | Contenido |
|---|---|
| `el-archivo-de-un-mob.md` | Referencia de todas las claves de `mobs/*.yml` |
| `skills.md` | Efectos, objetivos, rango, cooldown y probabilidad |
| `efectos.md` | Catálogo de efectos, pociones y mecánicas |
| `drops-y-objetos.md` | Tabla de drops y catalogo de objetos con NBT |
| `libro-y-mejoras.md` | Libro de inspeccion e items de mejora de mobs |
| `facciones-equipos-y-combate.md` | Facciones, Teams, actitudes y politica de ataque |
| `anclas-spawners-y-huevo.md` | Leash, ancla, spawners y el ciclo de vida del huevo |
| `monturas.md` | Monturas: variante, efectos, paso helado y cupo propio |
| `comandos-permisos-y-cupos.md` | Comandos, permisos y cupo por grupo |
| `configuracion.md` | `config.yml` linea por linea |
| `api-y-problemas.md` | API para otros plugins y problemas frecuentes |
| `desarrollo.md` | Compilacion, arquitectura, versionado y publicacion |
