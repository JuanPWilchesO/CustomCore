package com.juanp.custommobs.skill;

import java.util.List;

/**
 * Un paso de una cadena de interaccion.
 *
 * <p>Un paso puede decir algo ({@code message}), pedir dinero ({@code pay}) o ejecutar un
 * comando ({@code consoleCommand} / {@code playerCommand}), y decide a donde sigue: con
 * {@code options} espera un clic del jugador, con {@code next} sigue solo, y con {@code end}
 * cierra.
 *
 * @param id              nombre del paso, unico dentro de la cadena
 * @param message         texto que se manda al llegar (admite {@code {mob}} y {@code {jugador}})
 * @param options         botones clicables; si hay, la cadena espera al jugador
 * @param next            a donde sigue sin esperar a nadie
 * @param end             si cierra la conversacion
 * @param pay             cuanto dinero exige para seguir ({@code 0} = nada)
 * @param noPay           a donde va si no puede pagar; {@code null} = cierra
 * @param consoleCommand  comando de consola que ejecuta (solo aqui, en una interaccion)
 * @param playerCommand   comando que ejecuta el propio jugador
 */
public record ChainStep(
        String id,
        String message,
        List<ChainOption> options,
        String next,
        boolean end,
        double pay,
        String noPay,
        String consoleCommand,
        String playerCommand
) {

    /** {@code true} si el paso espera un clic del jugador. */
    public boolean asks() {
        return !this.options.isEmpty();
    }

    /** {@code true} si pide dinero para seguir. */
    public boolean charges() {
        return this.pay > 0.0D;
    }
}
