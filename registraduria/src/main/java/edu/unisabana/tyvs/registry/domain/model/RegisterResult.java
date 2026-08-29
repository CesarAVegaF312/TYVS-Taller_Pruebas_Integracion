package edu.unisabana.tyvs.registry.domain.model;

/**
 * Resultado posible de un intento de registro de votante.
 *
 * Cada constante corresponde a una clase de equivalencia del dominio. El
 * conjunto es EL MISMO que el del taller de pruebas unitarias: los dos
 * talleres describen la misma Registraduria, y una regla no puede cambiar
 * segun el taller desde el que se mire.
 */
public enum RegisterResult {
    /** Persona viva, mayor de edad, id valido y no registrada previamente. */
    VALID,
    /** Persona nula o con id invalido (id <= 0). */
    INVALID,
    /** Edad dentro del rango 0..17. */
    UNDERAGE,
    /** La persona no esta viva. */
    DEAD,
    /** El id ya fue registrado antes. */
    DUPLICATED,
    /**
     * Edad fuera del rango biologicamente posible (< 0 o > 120).
     *
     * Es una clase de equivalencia distinta de UNDERAGE, y la diferencia no es
     * cosmetica: una edad de 17 es un dato CORRECTO de una persona que aun no
     * puede votar, mientras que una de -1 es un dato IMPOSIBLE. Lo primero se
     * resuelve esperando; lo segundo, corrigiendo el registro. Devolver
     * UNDERAGE para -1 le diria a esa persona que espere a cumplir anios.
     */
    INVALID_AGE
}
