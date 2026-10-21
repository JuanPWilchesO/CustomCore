# Tutorial 11 — Interfaz y avisos

El chat queda para lo que tiene que quedar: errores, comandos y dialogos. Los estados y
avisos del juego se muestran en pantalla.

## Barras de estado

Cada estado activo tiene su boss bar, con nombre y cuenta atras:

- Controles: aturdimiento, raiz, silencio, desarme.
- Escudo de inmunidad.
- Efectos del plugin con duracion: invulnerabilidad, congelamiento, caida, robo de vida,
  atributos, sigilo, paso helado.

Se muestran hasta `ui.max-bars` a la vez (3 por defecto). Si hay mas, esperan.

## Avisos puntuales

Sin cuenta atras, duran `ui.notice-seconds` (2 por defecto):

- Flecha cargada o agotada.
- Inmunidad al control.
- Purga de efectos.
- Fin de un control.

## Cooldowns

- Si el material del item es unico en el catalogo: barrido vanilla en la toolbar.
- Si varios items comparten material: boss bar con prioridad maxima, mostrando el tiempo
  real de cooldown.

El cooldown real sigue en el servidor. `cooldown-aviso` ya no se usa.

## Recall

Los avisos de recall salen como titulo grande y centrado, con tiempos configurables.

## Mensajes de skill

- `message-display: auto` (por defecto): dialogo al chat; combate y estados a pantalla.
- `message-display: chat`: siempre al chat.
- `message-display: screen`: siempre a pantalla.

`effect: message` y `chain` van al chat aunque el modo sea `auto`.

## Configuracion

```yaml
ui:
  max-bars: 3
  notice-seconds: 2
  cooldown-notice-seconds: 3
  title-fade-in: 10
  title-stay: 40
  title-fade-out: 10
```

## Que sigue en el chat

- Comandos y permisos.
- Errores de crafteo, despliegue, guardado y restricciones.
- Libro de inspeccion y mejoras.
- MOTD.
- Dialogos y cadenas.

La action bar sigue siendo del HUD de faccion (amigo/enemigo).
