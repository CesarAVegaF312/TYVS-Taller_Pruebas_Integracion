package edu.unisabana.tyvs.registry.infrastructure.persistence;

import edu.unisabana.tyvs.registry.application.usecase.Registry;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.DockerClientFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRUEBA DE INTEGRACION CONTRA UNA BASE DE DATOS REAL (Testcontainers).
 *
 * Por que existe esta clase si ya tenemos RegistryIT con H2:
 *
 * H2 en memoria es rapido y comodo, pero NO es PostgreSQL. Difiere en
 * dialecto SQL, en tipos, en el manejo de mayusculas de los identificadores y
 * en el comportamiento transaccional. Una prueba verde contra H2 puede
 * ocultar un fallo que solo aparece en produccion. H2 no miente a proposito:
 * simplemente es otra base de datos.
 *
 * Testcontainers levanta un PostgreSQL de verdad en Docker, corre las pruebas
 * contra el y lo destruye al terminar. Mismo codigo de produccion, base de
 * datos real, cero instalacion manual.
 *
 * El precio: necesita Docker corriendo y tarda segundos en vez de
 * milisegundos. Por eso conviven las dos: H2 para el ciclo rapido durante el
 * desarrollo, PostgreSQL para la verificacion antes de integrar.
 *
 * Si Docker no esta disponible la clase entera se SALTA en vez de fallar: un
 * companero sin Docker no deberia ver el build en rojo por eso.
 *
 * OJO con COMO se hace ese salto. Un assumeTrue dentro de un @BeforeAll NO
 * sirve: la extension @Testcontainers arranca los contenedores estaticos en su
 * propio callback beforeAll, que se ejecuta ANTES, y el fallo ocurre al
 * intentar levantar el contenedor. Hay que usar @EnabledIf, que JUnit evalua
 * como condicion de ejecucion antes de invocar cualquier extension.
 */
@Testcontainers
@EnabledIf("hayDocker")
@DisplayName("RegistryRepository contra PostgreSQL real")
class RegistryRepositoryPostgresIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("registraduria")
                    .withUsername("tyvs")
                    .withPassword("tyvs");

    private RegistryRepository repo;
    private Registry registry;

    /** Condicion de ejecucion: se evalua ANTES de que la extension arranque nada. */
    static boolean hayDocker() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        repo = new RegistryRepository(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        repo.initSchema();
        repo.deleteAll();
        registry = new Registry(repo);
    }

    @Test
    @DisplayName("Persiste un votante valido y rechaza el duplicado")
    void shouldPersistAndRejectDuplicate() throws Exception {
        // Arrange
        Person ana = new Person("Ana", 100, 30, Gender.FEMALE, true);
        Person otra = new Person("AnaDos", 100, 40, Gender.FEMALE, true);

        // Act + Assert
        assertEquals(RegisterResult.VALID, registry.registerVoter(ana));
        assertTrue(repo.existsById(100));
        assertEquals(RegisterResult.DUPLICATED, registry.registerVoter(otra));
    }

    @Test
    @DisplayName("Los datos se leen igual que se escribieron")
    void shouldRoundTripRecord() throws Exception {
        // Arrange
        registry.registerVoter(new Person("Luis", 200, 45, Gender.MALE, true));

        // Act
        RegistryRecord guardado = repo.findById(200).orElseThrow();

        // Assert
        assertEquals(200, guardado.getId());
        assertEquals("Luis", guardado.getName());
        assertEquals(45, guardado.getAge());
        assertTrue(guardado.isAlive());
    }

    /**
     * La columna name es VARCHAR(100); insertamos 150 caracteres.
     *
     * Nota honesta: aqui H2 2.x se comporta IGUAL que PostgreSQL (ambos
     * rechazan el valor). Lo verificamos midiendo, no suponiendo. Se conserva
     * la prueba porque el limite de longitud si es una regla que vale la pena
     * fijar, pero no sirve como ejemplo de divergencia entre motores.
     */
    @Test
    @DisplayName("PostgreSQL rechaza un nombre mas largo que la columna")
    void shouldRejectOversizedName() {
        // Arrange: 150 caracteres en una columna VARCHAR(100)
        String nombreLargo = "A".repeat(150);
        Person p = new Person(nombreLargo, 300, 30, Gender.UNIDENTIFIED, true);

        // Act + Assert: el caso de uso envuelve el fallo de infraestructura
        assertThrows(RuntimeException.class, () -> registry.registerVoter(p));
    }

    /**
     * ESTA es la prueba que justifica todo el montaje: una divergencia REAL
     * entre H2 y PostgreSQL, comprobada ejecutando la misma consulta en ambos.
     *
     * El estandar SQL dice que los identificadores sin comillas se pliegan a
     * una caja, pero no dice a cual. Cada motor eligio distinto:
     *
     *   - H2 los pliega a MAYUSCULAS  -> la columna se llama NAME
     *   - PostgreSQL los pliega a minusculas -> la columna se llama name
     *
     * Un identificador ENTRECOMILLADO, en cambio, se toma literal. Por eso
     * SELECT "name" resuelve en PostgreSQL y falla en H2 con
     * "Columna \"name\" no encontrada", pese a que el CREATE TABLE fue
     * identico en los dos.
     *
     * Consecuencia para el taller: el mismo SQL NO es portable. Una consulta
     * validada contra H2 puede romperse en produccion, y al reves. Eso es
     * exactamente lo que Testcontainers evita.
     */
    @Test
    @DisplayName("PostgreSQL resuelve un identificador entrecomillado en minusculas (H2 no)")
    void shouldResolveQuotedLowercaseIdentifier() throws Exception {
        // Arrange
        registry.registerVoter(new Person("Ana", 400, 30, Gender.FEMALE, true));

        // Act: identificador entrecomillado en minusculas
        try (Connection con = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT \"name\" FROM registry WHERE id = 400")) {

            // Assert: en PostgreSQL funciona. La MISMA consulta contra H2 lanza
            // "Columna \"name\" no encontrada".
            assertTrue(rs.next());
            assertEquals("Ana", rs.getString(1));
        }
    }

    /**
     * Comprobacion directa de que estamos hablando con PostgreSQL y no con H2.
     * Util como diagnostico cuando una prueba se comporta distinto de lo esperado.
     */
    @Test
    @DisplayName("El motor realmente es PostgreSQL")
    void shouldBeRunningOnPostgres() throws SQLException {
        try (Connection con = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

            assertEquals("PostgreSQL", con.getMetaData().getDatabaseProductName());
        }
    }
}
