# Changelog

## 0.6.3 — avisos de skill y de objeto

- **Mensajes de inicio y fin** en skills (`inicio`, `fin`) y en objetos
  (`usar.inicio`, `usar.fin`). El aviso de fin se manda cuando caduca el efecto del plugin.
- **Aviso de espera** (`cooldown-aviso` / `usar.cooldown-aviso`) con `{segundos}`,
  `{tiempo}` o `{cooldown}`; se manda a quien intenta usar el objeto en espera.
- Los avisos van al jugador que castea o usa el objeto, no al objetivo.

## 0.6.2 — invulnerabilidad, atributos y teleport

- **Invulnerabilidad**: mecanica de efecto que cancela todo el dano mientras dure.
- **Atributos temporales**: mecanica `atributos` con `AttributeModifier`; lista de
  atributos de combate permitidos y renovacion al relanzar, sin acumular.
- **`teleport_behind`**: teletransporta al lanzador detras del objetivo y lo orienta
  hacia el. `distance` configura los bloques; `teleports.respect-protections`
  (por defecto `false`) decide si consulta protecciones.
- **`backstep`**: retrocede al lanzador y lo deja mirando al punto de origen.
- La jaula (mecanica 5) queda cancelada por ahora.

## 0.6.1 — proteccion de objetos y mecanicas de terreno

- **Modificaciones de objetos**: claves `modificable`, `encantar-libros`,
  `encantar-mesa`, `renombrar`, `reparar`, `desencantar` y `tallar` en `items/<nombre>.yml`.
  Se pueden cambiar con `/customcore item mod <nombre> <clave> <si|no>`.
  Bloquea yunque, mesa de encantamientos, libros, grindstone, mesa de herreria y Mending.
  La marca viaja en el NBT; las copias ya repartidas no cambian, las nuevas de `item give` si.
- **Una sola clase por jugador**: `class set` reemplaza la anterior; no se acumulan.
- **Pagos de cadena**: `pay-item` (material o id del catalogo), `pay-item-count` y `pay-xp`
  (niveles). Si se declaran varias formas, son alternativas; `no-pay` decide si no puede
  pagar con ninguna.
- **Nuevas mecanicas de skill**:
  - `block_below`: convierte los bloques bajo el objetivo (`block`, `block-radius`, `block-depth`).
  - `explosion`: explosion al lado del objetivo como ender crystal (`amount` = potencia,
    `break-blocks` para decidir si rompe terreno).
  - `repair_all`: repara todo el inventario/equipo del objetivo; respeta `reparar: false`.
- Documentacion y tutoriales actualizados.

## 0.6.0 — skills reutilizables, objetivos por mirada y efectos nuevos

- **Skills reutilizables**: un archivo por skill en `skills/` (id = nombre del archivo),
  con `ref:` desde los mobs. Los archivos invalidos o duplicados se avisan y se omiten.
- **Skills desde items**: `usar.skills` (lista) o `usar.skill` (una). El item decide
  `consume` y `cooldown`; el cooldown efectivo es el **mayor** entre el del item y el de
  las skills. `usar.efecto` sigue funcionando.
- **Objetivos nuevos**: `target-type: LOOK|RADIUS` + `target: ENEMIES|ALLIES|SELF`.
  `LOOK + ENEMIES` solo afecta al enemigo apuntado; `LOOK + ALLIES` solo al aliado.
  Se acaba el fuego amigo por accidente. Las skills viejas siguen cargando:
  `target: look` pasa a `LOOK + ENEMIES`; `enemies/allies/self` siguen siendo de radio.
- **Raycast de mirada** propio, con alcance configurable (tope 128) y corte por paredes.
- **Efectos de skill** `IGNITE` y `FREEZE`: fuego y congelacion durante `amount`
  segundos; `FREEZE` añade Lentitud II.
- **HEAL porcentual**: `porcentaje: true` hace que `amount` sea una fraccion de la vida
  maxima.
- **Purga de efectos**: `quitar_positivos` y `quitar_negativos`, con `tipo` en
  `effects.yml`; cubren efectos del plugin y de Minecraft (BENEFICIAL/HARMFUL).
- **Clic sobre entidades**: se atiende `PlayerInteractEntityEvent`, no solo
  `PlayerInteractEvent`; los items con `usar:` funcionan al hacer clic sobre un mob.
- **`items.ignore-cancelled-use`**: opcion para procesar el uso aunque otro plugin cancele
  el clic derecho al aire. Sigue respetando clase/faccion.
- **Restricciones centralizadas**: clase/faccion se comprueban antes de castear en clic,
  entidad y comida; `customcore.bypass` va separado de `customcore.admin`.
- **`/customcore testcast <skill>`**: lanza una skill desde la mirada, para probar
  distancias sin depender del clic (solo admin).
- **Fuego amigo en agro**: un golpe entre companeros de team sin faccion ya no deja marca
  de agresion.
- **Objetos**: bloqueo tambien al atacar, disparar arco/ballesta, lanzar tridente y pasar a
  la mano secundaria.

## 0.5.3 — reservar un objeto a una clase por comando

- **`/customcore item clase <nombre> <clase|nada>`**: igual que `item faccion`, pero para la
  clase. Con `nada` se libera. Avisa de que las copias ya repartidas no cambian (se reparten
  marcadas con `item give`).

## 0.5.2 — mobs invulnerables

- **`invulnerable: true`** en el yml de un mob: no recibe dano de **ninguna** causa (golpes,
  fuego, caida, pociones, vacio...). Por dentro rellena la lista `immune` con todas las causas,
  asi que se comporta igual que escribirlas todas. No impide retirarlo por comando.

## 0.5.1 — comprobar clase en las cadenas y fuera el nombre viejo

- **Cadenas de interaccion**: un paso puede exigir **clase** ademas de faccion, y decidir por
  donde sigue. En el paso: `clase: <id>` (o `*` = cualquiera) y `no-clase: <paso>`; con
  `faction:`/`no-faction:` ya existentes, se pueden montar flujos distintos por clase y faccion.
- **Nombre viejo fuera**: los alias de comando quedan en `/customcore` y `/cm`; los permisos
  pasan a `customcore.player` / `customcore.admin`; y docs, ejemplos y MOTD dicen CustomCore.
  El paquete Java interno sigue llamandose `com.juanp.custommobs` (no se ve; renombrarlo es un
  refactor aparte).

## 0.5.0 — clases de jugador

- **Clases**: se definen en `classes.yml` (`guerrero`, `mago`, `picaro`...) y se asignan con
  `/customcore class set|get|clear|list`. **No** definen alianzas ni enemistades: un jugador
  puede ser de cualquier clase en cualquier faccion.
- **Objetos con clase**: en `items/<nombre>.yml`, `clase: <id>`. Si el objeto declara tambien
  `faccion:`, solo lo usa quien sea de esa faccion **y** de esa clase (recoger, cofre, mover,
  equipar, usar y craftear). Un admin pasa siempre. Eso permite escenarios clase-faccion:
  un objeto de `atherea` + `guerrero` no lo usa nadie mas.
- **Pociones con clase**: `clase:` en `potions.yml`, con la misma regla.
- El repositorio de GitHub pasa a llamarse **CustomCore**.

## 0.4.0 — el proyecto pasa a CustomCore

- **Renombre**: plugin `CustomCore`, comando `/customcore` con alias `/cm`, `/cmobs` y
  `/custommobs`; la carpeta de datos se migra **por copia** desde `plugins/CustomMobs`.
- **Efectos y pociones custom**: `effects.yml` define un efecto y se usa en una pocion, una skill
  de mob o el `usar:` de un objeto. Cinco de arranque: congelamiento, caida, robo de vida,
  sigilo (vanish) y paso helado. `/customcore effects list|info` y `/customcore potion list|give`;
  recetas de pociones en `potions.yml`.
- **Action bar**: al mirar un mob custom dice Enemigo / Aliado / Neutral por jugador.
- **Arreglos**: el "Error de protocolo de red" al matar o guardar mobs (fuera el pintado de mobs
  por paquetes) y las placas fantasma.

## 0.3.8 — los objetos del catalogo no valen para recetas vanilla

- Un objeto guardado en el catalogo (con NBT) **ya no sirve de ingrediente** en una receta que no
  sea del plugin: si en la mesa hay uno y el resultado no es nuestro, el crafteo se cancela con un
  aviso. Asi un encantamiento guardado no acaba de relleno en un crafteo vanilla ajeno. La marca
  viaja dentro del objeto, asi que basta con mirar la rejilla.

## 0.3.7 — tus mobs a la vista, lobos en paz y objetos por faccion

- **Autocompletado de `despedir`**: al escribir `/cm despedir ` ya se ofrecen `todos` y los ids
  de **tus** mobs (los desplegados y los que guardas en huevos). Antes habia que saberse el id.
- **Craftear tambien respeta la faccion**: un objeto reservado solo lo craftea quien es de esa
  faccion. La receta puede salir en el libro de recetas de cualquiera —eso no se puede esconder
  por jugador—, pero el crafteo no se completa.
- **Color de faccion para los mobs de servidor**: cada faccion puede declarar en `factions.yml`
  su `color` (nombre) y su `glow` (brillo, y lo enciende), y sus mobs de categoria `server` se
  distinguen de un vistazo. Los mobs de **jugador** siguen con el color que elija su dueno o su
  team (`/cm color`, `/cm glow`). El color por faccion **entre jugadores** (enemigo rojo / aliado
  verde / neutral gris) no cambia.
- **Los disfraces no se colorean**: LibsDisguises dibuja el nombre por su cuenta —con armor
  stands y equipos propios, sin color— y se queda con el. Un mob que deba lucir color de faccion
  va **sin disfraz**; queda documentado.
- **Brillo por faccion, con refresco**: `faction-glow.refresh-seconds` (5 por defecto; `0` = en
  cada pasada). Antes el brillo se mandaba una sola vez y el cliente lo soltaba al moverse o al
  reaparecer —por eso desaparecia y la placa se quedaba—. Ahora se repite.
- **El que va agachado no muestra ni placa ni brillo**: se le oculta hasta que se levante.
- **Autocompletado de `faction`**: faltaba en la lista de administracion. Ademas completa sus
  subcomandos (`set`, `get`, `clear`, `list`), los jugadores conectados y los nombres de faccion.
- **`despedir`, ahora por mob**: con varios iguales, el tabulador ofrece cada uno con una marca
  corta (`mercenario_basico#a1b2c3d4`) y `despedir <id>#<marca>` retira **solo ese**. El id suelto
  deja de llevarse todos: si hay varios, lo dice y pide elegir. Antes se manejaba por definicion y
  despedir uno se llevaba por delante el resto. Y `despedir` vuelve a salir en el autocompletado
  (faltaba en la lista de administracion, asi que a un admin no le aparecia).
- **`cuota` lista tus mobs**: ademas de los numeros, lista cada mob con su **id** y su ubicacion,
  marca las monturas y saca los que estan **guardados en huevos** (que no son entidades vivas y
  no aparecian).
- **El libro trae el id util**: en cada pagina, bajo el nombre, el id — y ahora tambien la linea
  exacta para despedirlo (`/cm despedir <id>`).
- **`village-friendly` tambien con los lobos**: el lobo caza esqueletos, asi que un centinela
  amistoso con forma de esqueleto era su presa. Con la clave, ni el lobo lo apunta ni el mob
  ataca de vuelta — sin llegar a defenderlos (el lobo no es del pueblo).
- **Sin fuego amigo entre jugadores del mismo bando**: dos jugadores de la misma faccion
  (o de facciones aliadas, o del mismo team) no se hacen dano. Solo dentro de los mundos donde
  trabaja el plugin.
- **`worlds.enabled` vale para todo**: un mundo excluido no gestiona mobs, no invoca desde el
  huevo, no craftea huevos ni mejoras, y ni el libro ni los objetos reservados actuan ahi.
- **Tres funciones se pueden desligar de la lista**, cada una aparte, en `worlds.anywhere`:
  `item-faction-lock`, `player-friendly-fire` y `faction-glow`. En `true`, valen en todos los
  mundos; en `false` (por defecto), siguen `worlds.enabled`.
- **Objetos reservados a una faccion**: `/custommobs item faccion <nombre> <faccion|nada>` deja
  un objeto del catalogo para uso exclusivo de una faccion. Solo ellos pueden **recogerlo del
  suelo**, **sacarlo de un cofre**, **moverlo**, **equiparlo** o **usarlo**. Un admin pasa siempre.
  La marca viaja dentro del objeto (su NBT), asi que aguanta que lo
  suelten o lo guarde otro. No es una caja fuerte: impide **usarlo**, no arrebatarlo.
- **Teams ya no expulsa**: se cae lo del `teams.kick-command`. La incoherencia se **impide** (se
  rechaza la entrada, en los dos sentidos); al conectarse solo se **avisa en el log**.

## 0.3.6 — brillo de facciones, Teams coherente y targeting repasado

- **Brillo por faccion, para cada quien**: enemigos en rojo, aliados en verde, sin relacion en
  gris. Se manda **por espectador** con equipos falsos de **PacketEvents** (no se toca el
  scoreboard de verdad, asi que no interfiere con Teams). Modos en `faction-glow.mode`: `off`,
  `global` o `player` (cada uno lo decide con `/custommobs glowfaccion`). Necesita PacketEvents.
- **Coherencia con Teams, por los dos lados**: al **entrar** a un team cuyo jefe es de otra
  faccion, la entrada se **cancela** (con el `TeamMemberJoinEvent`, que es cancelable: no hace
  falta tocar Teams). Y al **asignarle faccion** a alguien que ya esta en un team de otra
  faccion, sale del team — ahi la API de Teams no deja sacar a nadie, asi que se usa su comando,
  configurable (`teams.kick-command`).
- **`transformable`**: decide si el juego puede convertir al mob (aldeano a bruja, zombi a
  ahogado, aldeano mordido a zombi aldeano...). Por defecto `true`, como siempre. Se apoya en el
  `EntityTransformEvent`, que es cancelable y trae el motivo (`INFECTION`, `LIGHTNING`, `DROWNED`,
  `PIGLIN_ZOMBIFIED`...).
- **Targeting repasado**: un mob de jugador cuenta con la faccion de su dueno en todo el sistema
  —ataca a los enemigos de su dueno, jugadores y mobs incluidos, y los mobs enemigos van a por
  el—. Y se cierra un hueco: un **jugador aliado** que golpeaba a un mob dejaba puesta la marca de
  agresion aunque el dano no contara; ahora no se marca si el que pega es del mismo bando, sea mob
  o jugador.

## 0.3.5 — comandos y conversaciones por skills

- **`console_command` y `player_command`**: ejecutar un comando desde consola, o hacer que lo
  ejecute el jugador. Con `command:` y los marcadores `{mob}` y `{jugador}`. **Solo valen con
  `mode: interaccion`** —o dentro de una cadena—: en pasiva o en combate la skill se descarta al
  cargar, con aviso, porque un mob que ejecuta comandos por su cuenta es un agujero de
  seguridad.
- **Cadenas de interaccion (`effect: chain`)**: una conversacion por pasos que el jugador
  recorre pulsando botones en el chat. Cada paso puede decir algo (`message`), preguntar
  (`options` con `label` y `next`), cobrar (`pay`) o ejecutar un comando, y decide a donde sigue
  (`next`, `end`). Si el jugador **no puede pagar**, la rama `no-pay` decide que pasa.
- **Economia por EssentialsX, por reflexion**: el plugin no depende de EssentialsX para compilar.
  **Ojo: las skills de pago no funcionan sin un plugin de economia** — sin EssentialsX, todo pago
  falla y la cadena va por `no-pay`. Se avisa en el log al arrancar.
- **Paso de faccion en las cadenas**: `faction` exige una faccion al jugador (`*` para pedir
  simplemente que tenga alguna) y `no-faction` decide a donde va si no la cumple.
- **Targeting integrado**: un mob de jugador **hereda la faccion de su dueno**. Ataca a los
  enemigos de su dueno —jugadores de faccion enemiga y los mobs de esos jugadores—, no toca a los
  de su faccion ni a los aliados (el fuego amigo por faccion tambien queda bloqueado), y los mobs
  de faccion enemiga van a por el como irian a por su dueno. Sin facciones asignadas, todo sigue
  como estaba.

## 0.3.4 — skill de invocacion (`summon`)

- **Nuevo efecto de skill `summon`**: el mob invoca copias de otra definicion a su alrededor.
  Se declara con `effect: summon`, `summon: <id>` (la definicion que aparece), `amount`
  (cuantas, tope 10 por uso) y `range` (el radio donde aparecen). Los invocados toman el dueno
  del invocador, y aparecen sobre el suelo mas alto de su columna si hay hueco. El `target` se
  ignora: siempre es alrededor del propio mob.

## 0.3.3 — arreglo: aliados que se peleaban sin hacerse dano

- **Los mobs del mismo bando —o de facciones aliadas— ya no se apuntan entre si, pase lo que
  pase.** Antes, la venganza pesaba mas que la faccion: un golpe amistoso —que el fuego amigo
  bloquea y por tanto **no hace dano**— dejaba igualmente la marca de agresion, y los dos mobs
  se quedaban atacandose sin poder hacerse nada, trabados hasta que expiraba. Ahora la faccion
  y la alianza se miran **antes** que la agresion, y ademas un golpe entre mobs del mismo bando
  ya no marca agresion ninguna.

## 0.3.2 — el mob amistoso defiende al pueblo

- **`village-friendly` ahora defiende a los aldeanos**, como un golem y mas radical: si un
  jugador le pega a un aldeano cerca —a mano o con un proyectil— todos los mobs amistosos que
  lo vean se lo apuntan como agresion y van a por el. Y reacciona **cualquier** mob amistoso,
  sea cual sea su `attitude` —incluso un `neutral` o un `defender`—: la agresion pesa mas que
  la actitud. Dura lo que diga `aggro-duration-seconds` y se percibe dentro de
  `aggro-watch-radius`.
- Recordatorio: que los aldeanos **no huyan** sigue dependiendo del **tipo de entidad** (no
  huyen de esqueletos). Si quieres otra apariencia, usa un tipo que no asusten y disfrazalo:
  el sistema de disfraces admite cualquier tipo de mob.

## 0.3.1 — monstruos para los mobs de servidor, y regiones en el spawneo

- **`attacks-monsters`**: los mobs de `category: server` ya pueden atacar a los **monstruos
  hostiles vanilla**, sea cual sea su `attitude`. Antes solo lo hacian los `defender`, asi
  que un `neutral` que se topaba con un zombi lo ignoraba.
- **Regiones en el spawneo aleatorio (WorldGuard)**: dos claves en la seccion `spawn:` del mob:
  `allowed-region` (si esta puesta, el mob solo aparece ahi; vacia = en cualquier parte) y
  `deny-regions` (lista de regiones donde no aparece). Si el punto cae en varias regiones
  nombradas y se contradicen, **manda la de mayor prioridad de WorldGuard**. Es una
  integracion **opcional**: sin WorldGuard, todo punto vale y el plugin funciona igual.

## 0.3.0 — convivencia, inmunidades y facciones de jugador

- **`village-friendly`**: el mob no apunta a aldeanos ni a golems, y los golems de hierro y
  de nieve no lo apuntan a el. Ojo con la otra mitad: que los aldeanos **no huyan** depende
  del **tipo de entidad** —huyen de zombis e illagers, no de esqueletos—, asi que para
  convivir con aldeanos conviene usar la familia **esqueleto**.
- **`immune`**: lista de causas de dano que el mob ignora, en **cualquier categoria** (montura,
  servidor, o mob de jugador anclado a el o a un bloque): `fall`, `potion` (la causa `magic`),
  `fire`, `drowning`, `lava`...
- **Facciones de jugador**: comando `/custommobs faction <set|get|clear|list>`, de
  administracion, guardado en `data/player-factions.yml` (fichero propio del plugin, sin
  depender de LuckPerms). Con faccion asignada, los mobs de faccion tratan al jugador por
  `factions.yml`: **los suyos y los aliados son intocables** —tambien sin fuego amigo— y los
  enemigos son objetivo. Sin faccion, manda la actitud de siempre, asi que nada cambia.

## 0.2.2 — el paso helado, de verdad

- **Paso helado de las monturas**: la 0.2.1 lo arreglo a medias. Faltaban tres cosas, y
  las tres solo se ven montando:
  - el hielo **se propagaba hacia abajo**: al congelarse la superficie, el bloque de debajo
    pasaba a tener hielo encima y tambien se congelaba, ciclo tras ciclo, hasta dejar bajo
    el caballo una columna que lo atrapaba;
  - al hundirse un poco, la superficie queda a su altura o por encima, y **congelarla ahi
    lo encerraba** y lo asfixiaba entre bloques;
  - a galope, o con un tiron de lag, congelar solo bajo las patas **llega tarde** y el
    caballo acaba nadando.
- Ahora solo se hiela el agua **con aire encima** (como el encantamiento), **por delante**
  en la direccion de la marcha, y **nunca el hueco del caballo**. Dos claves nuevas por
  montura: `frost-radius` (0 a 4, por defecto 2) y `frost-ahead` (0 a 8, por defecto 3).
  La comprobacion pasa a cada tick.

## 0.2.1 — arreglo del paso helado

- **Paso helado de las monturas**: el hielo se formaba un bloque **por debajo** de las
  patas, asi que en agua honda la superficie seguia siendo agua y la montura **nadaba en
  vez de caminar**. Ahora se congela la **superficie** —el agua que tiene algo que no es
  agua encima, como el encantamiento— en una ventana de dos niveles alrededor de las patas,
  y ademas **dos bloques por delante** en la direccion de la marcha, para que el suelo este
  listo a galope.

## 0.2.0 — monturas, aparicion natural y mejoras

- **Monturas** (`category: mount`): caballos y similares con variante, armadura, **efectos
  constantes** (al mob, a quien la monta o a los jugadores del bando dentro de un radio) y
  **paso helado**. Tienen **su propia cuenta de cupo**, aparte de los mobs que siguen y de
  los fijos. **No se teletransportan**: si las dejas atras se quedan, y la ventana de
  abandono se las lleva con aviso.
- **Aparicion aleatoria** (`spawn.natural`): los mobs de servidor pueden brotar solos por el
  mundo, de una sola vida y sin dejar spawner, con probabilidad, grupo, hora, luz, altura y
  tope propios. Es aparte de los spawners: solo suma.
- **Libro de inspeccion** renombrado a **Inventario de fuerzas**, con los valores en colores
  oscuros para que se lean sobre el papel claro.
- **Un mob custom no se modifica a mano**: se bloquean la etiqueta, la silla o armadura a
  mano, la correa, alimentarlo o criarlo y el equipado por dispensador.
- **Fuego amigo bloqueado**: tu propio mob, tus companeros de team y tus aliados no reciben
  dano de tu bando.
- **El huevo conserva las estadisticas** del mob al recogerlo y volverlo a colocar: la vida
  herida y las mejoras viajan con el (antes, guardarlo era una cura gratis).
- **`kill` alcanza los mobs en chunks descargadas**: carga su chunk un momento por el vinculo
  y los mata, asi que ya se limpia lo que quedo perdido.
- **`range` es alcance maximo tambien para `owner` y `target`**: una skill de montura ya no
  alcanza al dueno desde el otro lado del mundo.

## 0.1.0 — primera version publica

- **Mobs definidos por YAML**, un archivo por mob en `mobs/`. Dos categorias: `player`
  (con dueno, lo defiende) y `server` (sin dueno, con faccion y actitud).
- **Huevo-ficha**: el huevo no se gasta. Se liga al mob que invoca, lo recoge al
  golpearlo con el y lo vuelve a desplegar. Si el mob muere, el huevo queda inerte solo,
  sin tener que ir a buscarlo por el mundo.
- **Skills** con cuatro efectos (`damage`, `heal`, `message`, `potion`) y tres modos:
  `pasiva` (siempre, mientras el mob este cargado), `activa` (solo en combate) e
  `interaccion` (clic derecho, para dialogos).
- **Dos cuentas de cupo** independientes: mobs que siguen al dueno y mobs fijos a un
  bloque. Cada una con su cupo por grupo de LuckPerms, y no se pisan.
- **Zona cargada** alrededor del bloque de un mob fijo: no desaparece ni deja de contar
  aunque no haya jugadores cerca. Radio configurable por mob con `chunk-radius`.
- **Ventana de abandono**: un mob anclado a su dueno que no puede volver a su lado recibe
  un aviso y un plazo (60 s) antes de retirarse; su chunk se libera a los 120 s.
- **Facciones** con relaciones simetricas, integracion con **Teams** (bandos de jugadores)
  y **LibsDisguises** (apariencias), todo opcional y degradando por su cuenta.
- **Equipamiento** con encantamientos vanilla y custom, tabla de **drops** y **catalogo
  de objetos con NBT**.
- **Documentacion y tutoriales** dentro del propio jar, copiados a la carpeta del plugin
  al arrancar.

### Requisitos

- Servidor **Paper 1.21+** con **Java 21+** (probado en Paper 26.3).
- Para **compilar** hace falta **JDK 25**: `paper-api` 26.3 esta en bytecode de Java 25.
  El jar resultante, en cambio, es Java 21.

### Licencia

GPL-3.0. Si redistribuyes una version modificada, tiene que seguir siendo libre.
