package edu.unisabana.tyvs.certificados;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Cliente HTTP del servicio de Registraduria, visto desde el SERVICIO DE
 * CERTIFICADOS.
 *
 * Este es el CONSUMIDOR del contrato. En un proyecto real viviria en su propio
 * repositorio y su propio despliegue; aqui convive con el proveedor solo para
 * que el taller quepa en un proyecto.
 *
 * Lo unico que le importa a este consumidor del proveedor es:
 *   - que exista POST /register
 *   - que acepte un JSON con name, id, age, gender, alive
 *   - que responda 200 con el texto del resultado
 *
 * Eso, y nada mas que eso, es el contrato.
 */
public class RegistraduriaClient {

    private final String baseUrl;
    private final RestTemplate rest;

    public RegistraduriaClient(String baseUrl) {
        this(baseUrl, new RestTemplate());
    }

    public RegistraduriaClient(String baseUrl, RestTemplate rest) {
        this.baseUrl = baseUrl;
        this.rest = rest;
    }

    /**
     * Registra a un votante y devuelve el resultado tal como lo reporta la
     * Registraduria ("VALID", "DUPLICATED", "UNDERAGE", "INVALID_AGE", "DEAD", "INVALID").
     */
    public String registrarVotante(int id, String nombre, int edad, String genero, boolean vivo) {
        String json = String.format(
                "{\"name\":\"%s\",\"id\":%d,\"age\":%d,\"gender\":\"%s\",\"alive\":%b}",
                nombre, id, edad, genero, vivo);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp =
                rest.postForEntity(baseUrl + "/register", new HttpEntity<>(json, headers), String.class);

        return resp.getBody();
    }
}
