package com.juanp.custommobs.skill;

/**
 * Una opcion de una pregunta de la cadena: el boton que ve el jugador y a donde lleva.
 *
 * @param label texto del boton (admite {@code &} para color)
 * @param next  paso al que salta, o {@code null} para cerrar
 * @param end   si cierra la conversacion
 */
public record ChainOption(String label, String next, boolean end) {
}
