package edu.unisabana.tyvs.certificados;

/**
 * Caso de uso del SERVICIO DE CERTIFICADOS: emitir el certificado electoral
 * de una persona, pero solo si la Registraduria la acepto como votante valida.
 *
 * Este servicio no sabe nada de bases de datos ni de las reglas de la
 * Registraduria: solo conoce su CONTRATO HTTP. Si la Registraduria cambiara
 * ese contrato (por ejemplo, devolviendo {"resultado":"VALID"} en vez de texto
 * plano), este servicio se romperia en produccion sin que ninguna prueba del
 * proveedor se entere. Eso es exactamente lo que el contract testing previene.
 */
public class CertificadoService {

    private final RegistraduriaClient registraduria;

    public CertificadoService(RegistraduriaClient registraduria) {
        this.registraduria = registraduria;
    }

    /**
     * @return el numero de certificado si la persona es votante valida,
     *         o null si la Registraduria la rechazo.
     */
    public String emitirCertificado(int id, String nombre, int edad, String genero, boolean vivo) {
        String resultado = registraduria.registrarVotante(id, nombre, edad, genero, vivo);

        if (!"VALID".equals(resultado)) {
            return null;
        }
        return "CERT-" + id;
    }
}
