package edu.unisabana.tyvs.registry.application.usecase;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Clase de prueba unitaria para {@link Registry} utilizando un mock de {@link RegistryRepositoryPort}.
 *
 * <p>Estas pruebas ilustran cómo aislar el caso de uso del repositorio real,
 * aplicando dobles de prueba (Mockito) para simular los escenarios.</p>
 *
 * <p><b>Formato AAA:</b></p>
 * <ul>
 *   <li><b>Arrange</b>: se preparan datos y comportamiento del mock.</li>
 *   <li><b>Act</b>: se ejecuta el método bajo prueba.</li>
 *   <li><b>Assert</b>: se verifican resultados y que no haya interacciones no deseadas.</li>
 * </ul>
 *
 * <p><b>Beneficio:</b> este tipo de prueba es una <i>unitaria pura</i>,
 * sin necesidad de levantar bases de datos ni infraestructura adicional.</p>
 */
public class RegistryWithMockTest {

    /** Mock del puerto de persistencia. */
    private RegistryRepositoryPort repo;

    /** Caso de uso bajo prueba, instanciado con el mock. */
    private Registry registry;

    /**
     * Configura el mock y el caso de uso antes de cada prueba.
     *
     * <p>Se crea un mock de {@link RegistryRepositoryPort} usando Mockito
     * y se inyecta en la instancia de {@link Registry}.</p>
     */
    @Before
    public void setUp() {
        repo = mock(RegistryRepositoryPort.class);
        registry = new Registry(repo);
    }

    /**
     * Caso de prueba: detectar registros duplicados.
     *
     * <p><b>Escenario (BDD):</b></p>
     * <ul>
     *   <li><b>Given</b>: una persona con ID=7 y el repositorio ya indica que ese ID existe.</li>
     *   <li><b>When</b>: se intenta registrar la persona.</li>
     *   <li><b>Then</b>: el resultado debe ser {@link RegisterResult#DUPLICATED}
     *       y no se debe invocar el método {@code save(...)} en el repositorio.</li>
     * </ul>
     *
     * @throws Exception propagada en caso de error durante la ejecución.
     */
    @Test
    public void shouldReturnDuplicatedWhenRepoSaysExists() throws Exception {
        // Arrange: configurar mock y datos
        when(repo.existsById(7)).thenReturn(true);
        Person p = new Person("Ana", 7, 25, Gender.FEMALE, true);

        // Act: ejecutar método bajo prueba
        RegisterResult result = registry.registerVoter(p);

        // Assert: verificar resultado y comportamiento esperado del mock
        assertEquals(RegisterResult.DUPLICATED, result);
        verify(repo, never()).save(anyInt(), anyString(), anyInt(), anyBoolean());
    }

    /**
     * Caso de prueba: persona valida que si se persiste.
     *
     * <p><b>Given</b> el repositorio dice que el ID no existe;
     * <b>When</b> se registra la persona;
     * <b>Then</b> el resultado es {@link RegisterResult#VALID} y se invoca
     * {@code save(...)} exactamente una vez con esos datos.</p>
     *
     * <p>Note la diferencia con la aserción de estado: aqui no verificamos que
     * el dato quedo guardado (no hay base de datos), sino que el caso de uso
     * <i>colaboro</i> correctamente con su puerto. Eso es una aserción de
     * comportamiento, y es lo unico que un mock puede darnos.</p>
     */
    @Test
    public void shouldSaveWhenPersonIsValid() throws Exception {
        // Arrange
        when(repo.existsById(8)).thenReturn(false);
        Person p = new Person("Luis", 8, 30, Gender.MALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert
        assertEquals(RegisterResult.VALID, result);
        verify(repo, times(1)).save(8, "Luis", 30, true);
    }

    /**
     * Caso de prueba: fallo de infraestructura.
     *
     * <p><b>Given</b> el repositorio lanza una {@link java.sql.SQLException};
     * <b>When</b> se intenta registrar; <b>Then</b> el caso de uso la traduce a
     * {@link RegistryPersistenceException} en vez de dejarla escapar.</p>
     *
     * <p>Este escenario es practicamente imposible de provocar con una base de
     * datos real: es el ejemplo canonico de para que sirve un mock.</p>
     */
    @Test
    public void shouldWrapPersistenceFailure() throws Exception {
        // Arrange: el puerto falla al consultar
        when(repo.existsById(9)).thenThrow(new java.sql.SQLException("conexion perdida"));
        Person p = new Person("Eva", 9, 30, Gender.FEMALE, true);

        // Act + Assert
        try {
            registry.registerVoter(p);
            fail("Se esperaba RegistryPersistenceException");
        } catch (RegistryPersistenceException expected) {
            assertEquals(java.sql.SQLException.class, expected.getCause().getClass());
        }
    }

    /**
     * Caso de prueba: reglas de dominio que ni siquiera tocan el repositorio.
     *
     * <p>Verificamos ademas que NO se consulta la base de datos: rechazar a un
     * menor de edad no deberia costar una consulta.</p>
     */
    @Test
    public void shouldRejectUnderageWithoutTouchingRepository() throws Exception {
        // Arrange
        Person menor = new Person("Sara", 10, 17, Gender.FEMALE, true);

        // Act
        RegisterResult result = registry.registerVoter(menor);

        // Assert
        assertEquals(RegisterResult.UNDERAGE, result);
        verifyNoInteractions(repo);
    }

    /** Persona nula: validacion defensiva, sin tocar el repositorio. */
    @Test
    public void shouldReturnInvalidWhenPersonIsNull() {
        assertEquals(RegisterResult.INVALID, registry.registerVoter(null));
        verifyNoInteractions(repo);
    }

    /** Documento no positivo: entrada invalida. */
    @Test
    public void shouldReturnInvalidWhenIdIsNotPositive() {
        Person p = new Person("Nadie", 0, 30, Gender.UNIDENTIFIED, true);

        assertEquals(RegisterResult.INVALID, registry.registerVoter(p));
        verifyNoInteractions(repo);
    }

    /** Persona no viva: se rechaza antes de evaluar la edad. */
    @Test
    public void shouldReturnDeadWhenPersonIsNotAlive() {
        Person p = new Person("Pedro", 11, 50, Gender.MALE, false);

        assertEquals(RegisterResult.DEAD, registry.registerVoter(p));
        verifyNoInteractions(repo);
    }
}
