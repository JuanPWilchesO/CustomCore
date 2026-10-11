# Changelog

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
