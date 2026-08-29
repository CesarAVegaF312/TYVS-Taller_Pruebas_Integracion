# Taller de Pruebas de Integración y Sistema

Este taller tiene como objetivo aprender a diseñar, implementar y ejecutar **pruebas de integración** y **pruebas de sistema** en un proyecto Maven.
En el flujo de desarrollo de software, a diferencia de las **pruebas unitarias** (que verifican clases de forma aislada), las pruebas de integración y sistema permiten verificar cómo los **componentes interactúan entre sí** y cómo funciona el sistema **como un todo**.

---

## 🎯 Objetivo General

Comprender, diseñar e implementar **pruebas de integración y de sistema** sobre una aplicación con **arquitectura limpia**, usando herramientas como **JUnit**, **Mockito**, **H2**, **Testcontainers** y **Spring Boot Test**.

---

## 📑 Índice

- [PRUEBAS DE INTEGRACIÓN BÁSICAS](#pruebas-de-integración-básicas)
- [Prueba de Integración con BD H2](#prueba-de-integración-con-bd-h2)
- [Dobles de prueba para aislar el caso de uso](#dobles-de-prueba-para-aislar-el-caso-de-uso)
- [Bases de datos reales con Testcontainers](#bases-de-datos-reales-con-testcontainers)
- [Contract testing con Pact](#contract-testing-con-pact)
- [Prueba de Sistema caja negra](#prueba-de-sistema-caja-negra)
- [Ejecución de las pruebas](#ejecución-de-las-pruebas)
- [Buenas prácticas](#buenas-prácticas)
- [Para entregar](#para-entregar-con-este-taller)
- [Resumen del Taller](#hagamos-un-resumen)
- [Conclusión](#conclusión)
- [Recursos recomendados](#recursos-recomendados)

---

## Conceptos clave

- **Pruebas de integración**
Verifican que los módulos del sistema se comuniquen y trabajen juntos correctamente.
Ejemplo: la clase `Registry` (que valida votantes) + `RegistryRepository` (que guarda en la base de datos).

- **Pruebas de sistema**
Verifican el comportamiento del software como caja negra, a través de su interfaz pública (ej: endpoints HTTP, CLI).
Ejemplo: hacer un `POST /register` y validar la respuesta sin importar la implementación interna.

## CONOCE EL TALLER

### Estructura del Proyecto

Verifica los nuevos componentes en la estructura del ejercicio de la registraduría:

```text
src/main/java/edu/unisabana/tyvs/registry/
 ├─ RegistryApplication.java          # arranque de Spring Boot
 ├─ config/
 │   └─ RegistryConfig.java           # cableado: qué implementación se inyecta
 ├─ domain/model/                     # Person, Gender, RegisterResult
 ├─ application/
 │   ├─ usecase/                      # Registry, RegistryPersistenceException
 │   └─ port/out/                     # RegistryRepositoryPort
 ├─ infrastructure/persistence/       # RegistryRepository (JDBC), RegistryRecord
 └─ delivery/rest/                    # capa de exposición (inbound adapters)
     ├─ RegistryController.java
     └─ RegistryExceptionHandler.java # traduce excepciones a códigos HTTP

src/main/java/edu/unisabana/tyvs/registry/domain/model/rq/
 └─ PersonDTO.java                    # cuerpo JSON de la petición

src/test/java/edu/unisabana/tyvs/registry/
 ├─ application/usecase/
 │   ├─ RegistryWithMockTest.java     # UNITARIA  (mock del puerto)
 │   └─ RegistryIT.java               # INTEGRACIÓN (H2 real)
 ├─ infrastructure/persistence/
 │   └─ RegistryRepositoryPostgresIT.java  # INTEGRACIÓN (PostgreSQL en Docker)
 └─ delivery/rest/
     └─ RegistryControllerIT.java     # SISTEMA (HTTP de punta a punta)
```

> 📌 **La convención de nombres no es cosmética.** `*Test.java` lo ejecuta Surefire en `mvn test`; `*IT.java` lo ejecuta Failsafe en `mvn verify`. Por eso una prueba que toca una base de datos **nunca** debe llamarse `*Test`: la volvería parte del ciclo rápido y lo haría lento y frágil.

---

### Configuración de Dependencias

Agregamos dependencias y plugins clave al `pom.xml`.

```xml
  <dependencies>
    <!-- Motor vintage: ejecuta pruebas JUnit 4 sobre la plataforma JUnit 5.
         ES OBLIGATORIO mientras existan pruebas con @Before / org.junit.Assert. -->
    <dependency>
      <groupId>org.junit.vintage</groupId>
      <artifactId>junit-vintage-engine</artifactId>
      <version>5.10.2</version>
      <scope>test</scope>
    </dependency>

    <!-- JUnit 4: anotaciones y aserciones clásicas -->
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <version>4.13.2</version>
      <scope>test</scope>
    </dependency>

    <!-- Mockito para crear dobles de prueba -->
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <version>5.12.0</version>
      <scope>test</scope>
    </dependency>

    <!-- Web + JSON -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Trae JUnit 5, AssertJ, Hamcrest, Mockito y el soporte de test de Spring -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>

    <!-- Validación de @RequestBody con @Valid.
         Desde Spring Boot 2.3 ya NO viene incluida en spring-boot-starter-web. -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- H2: base de datos en memoria.
         scope runtime (no test): la aplicación también la necesita al ejecutarse. -->
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <version>2.2.224</version>
      <scope>runtime</scope>
    </dependency>

  </dependencies>
```

**Explicación:**

- `junit-vintage-engine`: es el **motor que permite ejecutar pruebas JUnit 4 sobre la plataforma JUnit 5**. No es "JUnit 5" ni una alternativa a JUnit 4: es el puente entre ambos.
- `junit` 4.13.2: las anotaciones y aserciones que usan las pruebas de este taller (`@Before`, `org.junit.Assert`).
- `spring-boot-starter-test`: trae JUnit 5 (Jupiter), AssertJ, Hamcrest, Mockito y el soporte de Spring. **Desde Spring Boot 2.4 ya no incluye vintage**, y por eso lo declaramos aparte.
- `mockito-core`: crea dobles de prueba, ideal cuando no quieres depender de IO real.
- `h2`: BD embebida que se crea en memoria → rápida, aislada, sin instalación.

> ⚠️ **Nunca excluya `junit-vintage-engine`.** Es un error frecuente: parece razonable "quitar JUnit 5 si uso JUnit 4", pero vintage es justamente lo que ejecuta sus pruebas JUnit 4. Si lo excluye, **las pruebas dejan de correr en silencio**: `mvn test` termina con `BUILD SUCCESS` y `Tests run: 0`. Es uno de los falsos verdes más difíciles de detectar, porque nada falla — simplemente no se prueba nada.

---

## PRUEBAS DE INTEGRACIÓN BÁSICAS

---

### Prueba de Integración con BD H2

Las pruebas de integración evalúan la **interacción entre múltiples módulos o capas**.
En este taller, se probará la relación entre el **caso de uso `Registry`** y el **adaptador `RegistryRepository`** (que usa una BD en memoria H2).

#### Ejemplo Base: `RegistryIT`

Crear el archivo: `src/test/java/edu/unisabana/tyvs/registry/application/usecase/RegistryIT.java`

> El sufijo **IT** (no `Test`) es deliberado: esta prueba abre una base de datos real, así que pertenece al ciclo de `mvn verify`, no al de `mvn test`.

Dentro de la clase agregar el método, lea atentamente la documentación de la clase:

```java
package edu.unisabana.tyvs.registry.application.usecase;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;
import edu.unisabana.tyvs.registry.infrastructure.persistence.RegistryRepository;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pruebas de integración para el caso de uso {@link Registry}, aplicando el formato AAA:
 * <ul>
 *   <li><b>Arrange</b>: preparación de datos y objetos a probar.</li>
 *   <li><b>Act</b>: ejecución del método bajo prueba.</li>
 *   <li><b>Assert</b>: verificación de los resultados esperados.</li>
 * </ul>
 */
public class RegistryIT {

    private RegistryRepositoryPort repo;
    private Registry registry;

    /**
     * Arrange común a todos los tests:
     * <ul>
     *   <li>Instancia un repositorio H2 en memoria.</li>
     *   <li>Inicializa el esquema (tabla) y limpia datos previos.</li>
     *   <li>Construye el caso de uso inyectando el repositorio.</li>
     * </ul>
     */
    @Before
    public void setup() throws Exception {
        String jdbc = "jdbc:h2:mem:regdb;DB_CLOSE_DELAY=-1";
        repo = new RegistryRepository(jdbc);

        repo.initSchema();   // Arrange: crear tabla
        repo.deleteAll();    // Arrange: limpiar datos previos

        registry = new Registry(repo); // Arrange: inyectar dependencia
    }

    /**
     * Caso de prueba:
     * <p>Una persona válida debe ser registrada exitosamente.</p>
     */
    @Test
    public void shouldRegisterValidPerson() throws Exception {
        // Arrange
        Person p1 = new Person("Ana", 100, 30, Gender.FEMALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p1);

        // Assert
        assertEquals(RegisterResult.VALID, result);
        assertTrue(repo.existsById(100));
    }

    /**
     * Caso de prueba:
     * <p>Al intentar registrar dos personas con el mismo ID:</p>
     * <ul>
     *   <li>La primera se guarda como válida.</li>
     *   <li>La segunda es rechazada como duplicada.</li>
     * </ul>
     */
    @Test
    public void shouldPersistValidVoterAndRejectDuplicates() throws Exception {
        // Arrange
        Person p1 = new Person("Ana", 100, 30, Gender.FEMALE, true);
        Person p2 = new Person("AnaDos", 100, 40, Gender.FEMALE, true);

        // Act (primer registro)
        RegisterResult result1 = registry.registerVoter(p1);

        // Assert primer registro
        assertEquals(RegisterResult.VALID, result1);
        assertTrue(repo.existsById(100));

        // Act (segundo registro con mismo ID)
        RegisterResult result2 = registry.registerVoter(p2);

        // Assert segundo registro
        assertEquals(RegisterResult.DUPLICATED, result2);
    }
}
```

#### Explicación paso a paso

1. **@Before → setup()** — anotación de JUnit 4, que es la que usa esta clase (en JUnit 5 el equivalente es `@BeforeEach`).
   - Configura una BD H2 en memoria (`jdbc:h2:mem:regdb;DB_CLOSE_DELAY=-1`).
   - Llama a `repo.initSchema()` para crear la tabla de votantes.
   - Crea un objeto `Registry` que usará ese `repo` real.

2. **Test**
   - Inserta a `p1` → el método `registry.registerVoter(p1)` ejecuta un `INSERT INTO registry(...)`.
   - Luego se hace una validación directa con `repo.existsById(100)` → consulta a la tabla H2 para confirmar que quedó.
   - Inserta a `p2` con mismo id → antes de intentar guardar, se hace un `SELECT` en la BD y detecta duplicado, devolviendo `DUPLICATED`.

👉 Así queda más claro: en la **primera llamada** se hace el insert, y en la **segunda llamada** se valida el duplicado consultando la base de datos.

#### Actividades con el uso de BD H2

1. Implementa pruebas para los siguientes casos (el de duplicados ya viene resuelto como ejemplo):
   - Menor de edad (`UNDERAGE`, edad=17)
   - Edad imposible (`INVALID_AGE`, edad=-1 o 121)
   - Persona fallecida (`DEAD`)
   - ID inválido (`INVALID`)
2. Aplica el formato **AAA (Arrange – Act – Assert)** en cada test.
3. Añade aserciones que verifiquen la persistencia real con H2.

#### 💡 Reto adicional con el uso de BD H2

Simula un error de conexión y observa cómo responde tu caso de uso. `Registry` traduce el fallo de infraestructura a una `RegistryPersistenceException`, de modo que la capa de entrega puede decidir el código HTTP sin conocer JDBC. Verifíquelo con un mock:

```java
when(repo.existsById(9)).thenThrow(new java.sql.SQLException("conexión perdida"));
assertThrows(RegistryPersistenceException.class, () -> registry.registerVoter(p));
```

Este escenario es prácticamente imposible de provocar con una base de datos real: es el ejemplo canónico de para qué sirve un mock.

---

### Dobles de prueba para aislar el caso de uso

Cuando no se desea usar una base de datos real, podemos **simular el repositorio** con Mockito.

> ⚠️ **Ojo con el nombre: esto NO es una prueba de integración.** Si todos los colaboradores están simulados, no se está integrando nada — es una prueba **unitaria** del caso de uso. La distinción importa porque las dos responden preguntas diferentes:
>
> | | `RegistryWithMockTest` (unitaria) | `RegistryIT` (integración) |
> |---|---|---|
> | Colaborador | Mock del puerto | `RegistryRepository` real sobre H2 |
> | Qué verifica | Que el caso de uso **colabora** bien (llamó a `save`, no lo llamó) | Que los datos **quedaron** realmente guardados |
> | Detecta | Errores de lógica de negocio | Errores de SQL, esquema, tipos, transacciones |
> | Velocidad | Milisegundos | Décimas de segundo |
> | No puede detectar | Que el `INSERT` está mal escrito | Poco; pero es más lenta y frágil |
>
> Se necesitan **ambas**. El error común es creer que los mocks reemplazan a la integración: un mock siempre responde lo que usted le dijo que respondiera, incluso si la base de datos real haría otra cosa.

#### Ejemplo Base: `RegistryWithMockTest`

Archivo: `src/test/java/edu/unisabana/tyvs/registry/application/usecase/RegistryWithMockTest.java`

```java
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
}

```

#### Explicación del Test con Mockito

- `mock(RegistryRepositoryPort.class)`: crea un doble de prueba.
- `when(repo.existsById(7)).thenReturn(true)`: simula que ya existe un votante con id 7.
- `assertEquals(...)`: validamos que el `Registry` responde `DUPLICATED`.
- `verify(...)`: asegura que nunca se llamó a `repo.save(...)` → es decir, no intentó grabar un duplicado.

👉 Aquí no usamos BD real, sino un **mock** para aislar la prueba a la interacción con el repositorio.

#### Actividades con Mockito

1. Implementa un mock del repositorio que devuelva `false` en `existsById()` y verifique que `save()` se invoca.
2. Implementa un mock que simule una excepción SQL y verifica que tu caso de uso la maneje correctamente.
3. Usa `verify(repo).save(...)` para confirmar la interacción esperada.

#### 💡 Reto adicional con Mocks

Crea una versión **FakeRepository** que guarde los datos en una `HashMap` en memoria sin usar Mockito.

---

---

### Bases de datos reales con Testcontainers

H2 es rápida y no requiere instalación, pero tiene un problema que conviene mirar de frente: **no es la base de datos de producción**. Difiere en dialecto SQL, en tipos, en el plegado de identificadores y en el comportamiento transaccional. Una prueba verde contra H2 puede ocultar un fallo que solo aparece cuando el código llega a PostgreSQL.

**Testcontainers** resuelve eso: levanta un motor real dentro de un contenedor Docker desde la propia prueba, ejecuta contra él, y lo destruye al terminar. Sin instalar nada a mano y sin contaminar la máquina.

#### Una divergencia real, medida

Este no es un riesgo teórico. Ejecutando el **mismo** `CREATE TABLE` y la **misma** consulta en los dos motores:

```text
H2       ::  SELECT "name" FROM registry  ->  FALLA: Columna "name" no encontrada
POSTGRES ::  SELECT "name" FROM registry  ->  OK, valor = Ana
```

¿Por qué? El estándar SQL dice que los identificadores sin comillas se pliegan a una caja, pero **no dice a cuál**, y cada motor eligió distinto:

| | Identificador sin comillas | Resultado |
|---|---|---|
| **H2** | se pliega a MAYÚSCULAS | la columna se llama `NAME` |
| **PostgreSQL** | se pliega a minúsculas | la columna se llama `name` |

Un identificador **entrecomillado**, en cambio, se toma literal. De ahí que `SELECT "name"` resuelva en PostgreSQL y falle en H2, pese a que el esquema se creó con la misma sentencia.

Conclusión para el taller: **el mismo SQL no es portable**. Es exactamente el tipo de defecto que una prueba con H2 nunca encontrará.

#### Dependencias (`pom.xml`)

```xml
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <version>1.19.8</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>1.19.8</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
```

> ⚠️ **Prerrequisito**: Docker debe estar corriendo. Es el único punto del taller que lo exige. Verifíquelo con `docker version` antes de continuar.

#### Ejemplo base: `RegistryRepositoryPostgresIT`

```java
@Testcontainers
class RegistryRepositoryPostgresIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("registraduria")
                    .withUsername("tyvs")
                    .withPassword("tyvs");

    @BeforeAll
    static void requiereDocker() {
        // Si no hay Docker, la prueba se SALTA en vez de fallar.
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no está disponible: se omiten las pruebas con Testcontainers");
    }

    @BeforeEach
    void setUp() throws Exception {
        repo = new RegistryRepository(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        repo.initSchema();
        repo.deleteAll();
        registry = new Registry(repo);
    }

    // ... las pruebas usan el MISMO código de producción que con H2
}
```

Note dos detalles de diseño:

1. **El contenedor es `static`.** Se levanta una vez por clase, no una por prueba. Arrancar PostgreSQL cuesta segundos; hacerlo en cada método volvería la suite inusable.
2. **`assumeTrue` en vez de fallar.** Un compañero sin Docker verá las pruebas como *omitidas*, no como *rotas*. Una prueba que falla por falta de infraestructura enseña al equipo a ignorar el rojo.

#### Cuándo usar cada una

No se trata de reemplazar H2 por Testcontainers, sino de saber para qué sirve cada una:

| | H2 | Testcontainers |
|---|---|---|
| Velocidad | milisegundos | segundos |
| Fidelidad | aproximada | total |
| Prerrequisitos | ninguno | Docker |
| Cuándo | ciclo rápido mientras desarrolla | antes de integrar, y en CI |

#### Actividades con Testcontainers

1. Ejecute `mvn clean verify` y compare el tiempo de `RegistryIT` (H2) con el de `RegistryRepositoryPostgresIT`. ¿Cuánto cuesta la fidelidad?
2. Escriba una consulta que funcione en H2 y falle en PostgreSQL (o al revés). Documéntela en su Wiki.
3. Cambie la imagen a `postgres:13-alpine`. ¿Siguen pasando todas las pruebas? Ese es, en una línea, el valor de poder fijar la versión del motor.

---

### Contract testing con Pact

Llegamos al hueco que ninguna de las técnicas anteriores cubre.

Imagine que la Registraduría deja de responder texto plano y empieza a responder `{"resultado":"VALID"}`. ¿Qué prueba se entera?

| Prueba | ¿Detecta el cambio? | Por qué |
|---|---|---|
| `RegistryWithMockTest` (mock) | ❌ No | El mock responde lo que usted le dijo, no lo que responde el servicio real |
| `RegistryIT` (H2) | ❌ No | No pasa por HTTP |
| `RegistryControllerIT` (sistema) | ❌ No | Verifica al proveedor **contra sí mismo**; si actualiza la prueba junto con el código, sigue verde |
| Pruebas del consumidor | ⚠️ Sí, pero tarde | En su propio pipeline, o peor: en producción |

Ese es el problema real de los sistemas distribuidos: **cada equipo prueba su parte y todas pasan, pero el sistema completo está roto**. La respuesta tradicional —levantar todos los servicios y probarlos juntos— es lenta, frágil y no escala.

#### La idea: el consumidor escribe el contrato

En el *consumer-driven contract testing*:

1. El **consumidor** declara qué espera del proveedor y ejecuta sus pruebas contra un servidor simulado que se comporta así. Como subproducto genera un archivo de **pacto**.
2. El **proveedor** toma ese pacto y verifica, **en su propio pipeline**, que sigue cumpliéndolo.

Los dos servicios nunca se levantan al mismo tiempo. Cada equipo corre su parte cuando quiere, y aun así el acuerdo queda verificado de punta a punta.

#### El consumidor de este taller

El enunciado de la Registraduría siempre mencionó que *"se generarán los certificados electorales de aquellas personas cuyo voto sea válido"*, pero ese servicio nunca existió. Ahora sí: `edu.unisabana.tyvs.certificados` es un servicio que pide a la Registraduría registrar un votante y emite el certificado solo si la respuesta es `VALID`.

> 📌 En un proyecto real, `certificados` viviría en **otro repositorio, con otro despliegue y otro equipo**. Aquí convive con el proveedor solo para que el taller quepa en un proyecto. Lo que importa es que el consumidor no conoce ni la base de datos ni las reglas de negocio de la Registraduría: **solo su contrato HTTP**.

#### Lado consumidor

```java
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "registraduria", port = "0", pactVersion = PactSpecVersion.V3)
class CertificadoServicePactTest {

    @Pact(consumer = "certificados", provider = "registraduria")
    public RequestResponsePact votanteValido(PactDslWithProvider builder) {
        return builder
                .given("no hay ningún votante registrado con id 900")   // estado requerido
                .uponReceiving("un registro de votante válido")
                .path("/register").method("POST").headers(JSON)
                .body("{\"name\":\"Ana\",\"id\":900,\"age\":30,\"gender\":\"FEMALE\",\"alive\":true}")
                .willRespondWith()
                .status(200).body("VALID")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "votanteValido")
    void emiteCertificadoCuandoElVotanteEsValido(MockServer mockServer) {
        CertificadoService servicio =
                new CertificadoService(new RegistraduriaClient(mockServer.getUrl()));

        assertEquals("CERT-900", servicio.emitirCertificado(900, "Ana", 30, "FEMALE", true));
    }
}
```

Al ejecutarlo se genera `target/pacts/certificados-registraduria.json`.

#### Lado proveedor

```java
@Provider("registraduria")
@PactFolder("target/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistraduriaProviderPactIT {

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificarPacto(PactVerificationContext context) {
        context.verifyInteraction();   // reproduce TODAS las interacciones del pacto
    }

    /** Cada @State corresponde a un "given" del pacto. */
    @State("ya existe un votante registrado con id 901")
    void conVotante901() {
        registry.registerVoter(new Person("Luis", 901, 40, Gender.MALE, true));
    }
}
```

El `@State` es la pieza que suele costar entender: el consumidor **nombra** el estado que necesita (`"ya existe un votante con id 901"`), pero es el proveedor quien sabe **cómo montarlo**. El consumidor no sabe que hay una tabla, y no debe saberlo.

#### Compruébelo usted mismo

La mejor forma de convencerse de que esto no es decorativo:

1. Ejecute `mvn clean verify`. Todo en verde.
2. En `RegistryController`, cambie `return r.name();` por `return "{\"resultado\":\"" + r.name() + "\"}";`
3. Ejecute `mvn verify` otra vez.

`RegistryControllerIT` seguirá pasando si usted también actualiza su aserción — pero **la verificación del pacto falla**, porque el consumidor sigue esperando texto plano. Ese es exactamente el fallo que, sin Pact, habría llegado a producción.

4. Revierta el cambio y confirme que vuelve a verde.

#### Actividades con Pact

1. Agregue una tercera interacción al pacto (por ejemplo, un votante menor de edad) con su `@State` correspondiente.
2. Cambie el nombre del campo `id` a `documento` **solo en el consumidor**. ¿Qué prueba falla y en qué lado?
3. Investigue qué es un **Pact Broker** y explique en el Wiki por qué leer los pactos de una carpeta local no sirve cuando los equipos están separados.

### Prueba de Sistema caja negra

Las pruebas de sistema validan el **comportamiento del sistema completo**, incluyendo controladores HTTP, lógica de negocio y persistencia.

#### Ejemplo Base: `RegistryControllerIT`

Archivo: `src/test/java/edu/unisabana/tyvs/registry/delivery/rest/RegistryControllerIT.java`

```java
// src/test/java/edu/unisabana/tyvs/registry/delivery/rest/RegistryControllerIT.java
package edu.unisabana.tyvs.registry.delivery.rest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Base de datos propia: H2 con DB_CLOSE_DELAY=-1 vive mientras viva la JVM, y
// Surefire/Failsafe reutilizan la JVM entre clases. Sin un nombre distinto, un
// id insertado aquí reaparecería en otra prueba.
@TestPropertySource(properties = "registry.jdbc-url=jdbc:h2:mem:regdb_ctrl_it;DB_CLOSE_DELAY=-1")
public class RegistryControllerIT {

    // No define beans propios: el cableado real vive en RegistryConfig. Si la
    // prueba lo duplicara, estaría probando su propio cableado en vez del de
    // producción, y además rompería el contexto por nombres de bean repetidos.

    @Autowired
    private TestRestTemplate rest;

    @Test
    public void shouldRegisterValidPerson() {
        String json = "{\"name\":\"Ana\",\"id\":100,\"age\":30,\"gender\":\"FEMALE\",\"alive\":true}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.postForEntity("/register", new HttpEntity<>(json, headers), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("VALID", resp.getBody());
    }
}
```

#### Explicación del Test de sistemas

- **System under test:** un servidor mínimo que expone `/register`.
- El test **no sabe nada de clases internas** (`Registry`, `Person`) → solo valida que si hago un `POST`, la respuesta es correcta.
- Esto es lo más parecido a cómo un **cliente real** interactuaría con el sistema.

#### Actividades con Sistemas

1. Realiza pruebas con distintos cuerpos JSON que produzcan los estados `VALID`, `DUPLICATED`, `UNDERAGE`, `INVALID_AGE`, `DEAD`.
2. Usa Postman o curl para verificar los endpoints `/register` y documenta tus observaciones.
3. Implementa una prueba negativa (JSON incompleto o tipo incorrecto).

#### 💡 Reto adicional con Sistemas

Agrega validaciones con `@Valid` en el `PersonDTO` y prueba que el sistema devuelva errores HTTP adecuados (`400`, `409`, `422`).

---

### Ejecución de las pruebas

- **Solo las unitarias** (rápido, sin base de datos ni Docker). Ejecuta los `*Test.java` vía Surefire:

```bash
mvn test
```

- **Todo: unitarias + integración + sistema.** Añade los `*IT.java` vía Failsafe, incluidos los de Testcontainers:

```bash
mvn verify
```

> ⚠️ `mvn test` **no ejecuta los `*IT.java`**. Si usa `test` para verificar su trabajo verá `BUILD SUCCESS` sin haber probado ninguna integración. Para eso está `verify`.

Reporte de cobertura **combinado** (unitarias + integración) con JaCoCo:

```text
target/site/jacoco/index.html            # unitarias (Surefire)
target/site/jacoco-it/index.html         # integración (Failsafe)
```

El `pom.xml` declara las cuatro ejecuciones de JaCoCo (`prepare-agent`, `report`, `prepare-agent-integration`, `report-integration`). Sin las dos últimas, lo que cubren los `*IT` no contaría y el 80% global sería inalcanzable.

---

## Automatización e integración (Opcional)

- Ejecuta las pruebas de integración en cada commit con CI (GitHub Actions, Jenkins, GitLab CI).
- Rechaza merges si `mvn verify` falla.

🎓 Esta guía presenta el proceso para la creación y configuración de flujos de Integración Continua (CI) utilizando GitHub Actions.
Puedes consultarla en el siguiente enlace: [**Taller de Integración Continua en GitHub**](https://github.com/CesarAVegaF312/DAYS-Integracion_continua/tree/main/github).

---

## Buenas prácticas

1. **Separación clara:** `*Test.java` → unitarias, `*IT.java` → integración/sistema.
2. **Datos aislados:** usar BD en memoria (H2) evita que las pruebas dependan de un entorno externo.
3. **Mocks en los límites:** Mockito es útil para pruebas rápidas cuando no quieres depender de IO real.
4. **Pruebas de sistema = caja negra:** siempre probar por interfaces externas (API, CLI, UI).

---

## PARA ENTREGAR CON ESTE TALLER

### 1) Repositorio

- **Repositorio Git** con el proyecto completo y **URL pública o acceso por invitación**.
- Archivo **`.gitignore`** (excluir `target/`, `.idea/`, `.vscode/`, etc.).
- Archivo **`integrantes.txt`** o sección en el README con nombres y correos institucionales.
- **Rama principal ejecutable:** debe compilar y correr con `mvn clean verify` sin configuraciones manuales adicionales.

### 2) Documentación en Wiki (obligatoria)

> Toda la documentación del taller se entrega en el **Wiki del repositorio**.
> No se requiere PDF; el Wiki es la entrega oficial.

Estructura mínima sugerida del Wiki:

- **Inicio:** descripción breve del dominio, propósito del sistema y miembros del equipo.
- **Tipos de pruebas:** diferencia clara entre unitarias, integración y sistema (tabla o esquema).
- **Arquitectura limpia:** diagrama de capas usadas (`domain`, `application`, `infrastructure`, `delivery`).
- **Pruebas de Integración:** explicación de cómo se conectan las capas y la base de datos (H2 o mock).
- **Pruebas con Mockito:** ejemplos de uso de `when(...)`, `verify(...)`, `never(...)`.
- **Pruebas de Sistema (HTTP):** escenarios y evidencias de ejecución (capturas o respuestas JSON).
- **Resultados:** capturas del **reporte JaCoCo** y breve análisis de cobertura.
- **Conclusiones técnicas:** aprendizajes y limitaciones detectadas.

Incluye **enlaces al código** (`Registry.java`, `RegistryController.java`, tests) dentro de cada sección del Wiki.

### 3) Pruebas de Integración

- Al menos **4 pruebas con base de datos H2** cubriendo interacciones reales entre `Registry` y `RegistryRepository`.
- Casos mínimos:
  - Persona válida → `VALID`
  - Persona duplicada → `DUPLICATED`
  - Persona menor de edad → `UNDERAGE`
  - Persona con edad imposible → `INVALID_AGE`
  - Persona fallecida → `DEAD`
- Deben ejecutarse sin mocks, verificando que los datos se persisten realmente.
- Usa formato **AAA (Arrange – Act – Assert)** y nombres descriptivos (por ejemplo `shouldReturnDuplicatedWhenIdAlreadyRegistered()`, como en `RegistryControllerIT`).

### 4) Pruebas de Integración con Mocks

- Al menos **2 pruebas con Mockito**, simulando el repositorio o adaptador externo.
- Verificar interacciones con:
  - `verify(repo).save(...)`
  - `verify(repo, never()).save(...)`
- Incluir un caso de excepción controlada (`when(repo.save(...)).thenThrow(...)`) y manejo correcto del error.
- Comenta brevemente el propósito de cada test y la lógica simulada.

### 5) Pruebas de Sistema (HTTP)

- Al menos **2 pruebas end-to-end** usando:
  - `TestRestTemplate`, `MockMvc` o cliente HTTP equivalente.
- Validar los endpoints reales (`/register`) devolviendo respuestas HTTP correctas (`200` para una petición válida, `400` para datos inválidos del cliente).
- Casos mínimos:
  - Registro exitoso (status 200, body “VALID”).
  - Entrada inválida o inconsistente (status 400 / 422).
- Adjuntar en el Wiki **capturas del resultado** (Postman o terminal).

### 6) Cobertura (JaCoCo)

- Reporte **JaCoCo** generado en `target/site/jacoco/index.html`.
- **Cobertura global ≥ 80%**, y al menos **70% en el paquete `application` y `delivery`**.
- Adjuntar capturas en el Wiki e indicar **qué clases no se pudieron cubrir y por qué** (p. ej. excepciones controladas, código legado, etc.).

### 7) Matriz de pruebas de integración

- Tabla con los **casos de integración** probados:
  - **Caso**, **Entrada**, **Resultado esperado**, **Tipo de prueba (H2/Mock/HTTP)**, **Test que lo valida**.

**Ejemplo:**

| Caso | Entrada | Resultado esperado | Tipo | Test que lo valida |
|------|---------|--------------------|------|--------------------|
| Persona válida | id=100, edad=30, viva | `VALID` | H2 | `RegistryIT.shouldRegisterValidPerson()` |
| Persona duplicada | id=100 registrado dos veces | `DUPLICATED` | H2 | `RegistryIT.shouldPersistValidVoterAndRejectDuplicates()` |
| Duplicado sin base de datos | el mock dice que el id existe | `DUPLICATED` | Mock | `RegistryWithMockTest.shouldReturnDuplicatedWhenRepoSaysExists()` |
| Fallo de persistencia | el puerto lanza `SQLException` | `RegistryPersistenceException` | Mock | `RegistryWithMockTest.shouldWrapPersistenceFailure()` |
| Persona menor de edad | id único, edad=17 | `UNDERAGE` | HTTP | `RegistryControllerIT.shouldReturnUnderageWhenPersonIsMinor()` |
| Edad imposible | edad=-1 | `INVALID_AGE` | Mock | `RegistryWithMockTest.shouldReturnInvalidAgeWhenAgeIsNegative()` |
| Valor límite entre las dos | edad=0 | `UNDERAGE` | Mock | `RegistryWithMockTest.shouldReturnUnderageWhenAgeIsZero()` |
| Género inválido | `gender="X"` | HTTP 400 | HTTP | `RegistryControllerIT.shouldReturnBadRequestWhenGenderIsNotValid()` |
| Dialecto SQL divergente | `SELECT "name"` | resuelve en PostgreSQL | Testcontainers | `RegistryRepositoryPostgresIT.shouldResolveQuotedLowercaseIdentifier()` |
| Contrato con el consumidor | pacto de `certificados` | interacciones verificadas | Pact | `RegistraduriaProviderPactIT.verificarPacto()` |

### 8) Gestión de defectos

- Archivo **`defectos.md`** con al menos **1 defecto real o simulado** detectado por pruebas de integración o sistema.
  - **Caso probado**
  - **Resultado esperado vs. obtenido**
  - **Causa probable**
  - **Estado:** Abierto / En progreso / Resuelto
  - **Evidencia:** fragmento de log o screenshot

### 9) Calidad del código

- Clases sin duplicación ni dependencias cíclicas.
- Constantes reutilizables (`MIN_AGE`, `MAX_AGE`, etc.).
- Nombrado claro y uso correcto de paquetes.
- Control de errores con excepciones específicas y manejo en el controlador HTTP.
- Eliminación de código comentado o redundante.

### 10) Reflexión final (en el Wiki)

- ¿Qué capas fueron más difíciles de probar y por qué?
- ¿Qué beneficios observas en usar mocks frente a H2 o base real?
- ¿Cómo mejorarías el diseño de `RegistryController` o `RegistryRepository` para facilitar las pruebas automáticas?
- ¿Qué aprendiste sobre **integración continua (CI)** al ejecutar tus pruebas con Maven y JaCoCo?

### 11) Rúbrica – Taller de Pruebas de Integración y Sistema

| **Criterios de evaluación** | **Indicadores de cumplimiento** | **Excelente (5 pts)** | **Bueno (4 pts)** | **Necesita mejorar (3.5 pts)** | **Deficiente (2.5 pts)** | **No cumple (0 pts)** |
|-----------------------------|----------------------------------|------------------------|-------------------|-------------------------------|--------------------------|------------------------|
| **Estructura del proyecto y repositorio** | El repositorio está correctamente organizado, con `.gitignore`, ramas compilables y documentación básica. | Estructura limpia, compilable con `mvn clean verify`, incluye `.gitignore` y documentación. | Compila correctamente, estructura clara con mínimos ajustes. | Estructura parcialmente ordenada, requiere ajustes menores. | Errores de compilación o estructura desordenada. | No entrega o el código no ejecuta. |
| **Documentación en Wiki** | Contiene secciones completas (Inicio, tipos de pruebas, resultados, reflexión, etc.) con enlaces al código. | Wiki completo, claro y con enlaces a todas las clases y tests. | Wiki completo con leves omisiones o sin algunos enlaces. | Wiki incompleto o con poca claridad. | Wiki muy limitado o confuso. | No hay Wiki o está vacío. |
| **Pruebas de integración (H2)** | Implementa pruebas reales entre `Registry` y `RegistryRepository`. | ≥3 pruebas completas y funcionales, usando H2 y patrón AAA. | Pruebas funcionales pero con cobertura parcial. | Pruebas incompletas o sin verificación clara de persistencia. | Escenarios incorrectos o sin H2 configurado. | No existen pruebas de integración. |
| **Pruebas con mocks (Mockito)** | Uso de mocks y verificación de interacciones. | ≥2 pruebas con Mockito usando `when`, `verify`, `never`, etc. correctamente. | Pruebas correctas pero con poca variedad o validación parcial. | Usa mocks sin verificar interacciones o comportamiento. | Configuración incorrecta de mocks. | No existen pruebas con mocks. |
| **Pruebas de sistema (HTTP)** | Validación de endpoints reales con TestRestTemplate o MockMvc. | ≥2 pruebas HTTP completas (200 y 400), con aserciones reales (`assertEquals`, no la palabra clave `assert`). | Pruebas funcionales pero con casos limitados. | Pruebas incompletas o con endpoints incorrectos. | Pruebas fallidas o sin conexión al servidor. | No existen pruebas HTTP. |
| **Bases de datos reales (Testcontainers)** | Ejecución de pruebas contra un motor real en Docker. | ≥2 pruebas con Testcontainers y una divergencia H2/PostgreSQL documentada. | ≥2 pruebas correctas, sin analizar divergencias. | 1 prueba, o el contenedor mal configurado (no estático). | Configurado pero sin pruebas que lo usen. | No usa Testcontainers. |
| **Contract testing (Pact)** | Contrato consumidor-proveedor verificado en ambos lados. | Pacto con ≥3 interacciones, verificación del proveedor en verde y demostración de que detecta una ruptura. | Pacto y verificación correctos, sin demostrar la ruptura. | Solo el lado consumidor, o sin ``. | Configurado pero el pacto no se verifica. | No aplica contract testing. |
| **Cobertura de pruebas (JaCoCo)** | Nivel de cobertura global y por capa. | ≥80% global y ≥70% en `application` y `delivery`. | Entre 70–79% global, sin grandes omisiones. | Cobertura media (50–69%) o irregular. | Cobertura <50%. | No presenta reporte o no genera cobertura. |
| **Matriz de pruebas** | Tabla de casos probados y correspondencia con métodos de test. | Matriz completa, clara y actualizada. | Matriz parcial con algunos casos omitidos. | Matriz incompleta o sin correspondencia con código. | Matriz confusa o sin formato. | No entrega matriz. |
| **Gestión de defectos** | Registro de defectos y análisis. | Documento `defectos.md` con al menos 1 caso bien analizado. | Documento con casos simulados pero comprensibles. | Documento incompleto o superficial. | Caso sin análisis o sin evidencias. | No entrega `defectos.md`. |
| **Calidad del código** | Claridad, limpieza y consistencia del código. | Código limpio, sin duplicaciones, constantes extraídas, buen uso de excepciones. | Código comprensible con leves redundancias. | Código con duplicación o nombres poco claros. | Código confuso o sin buenas prácticas. | Código desorganizado o con errores graves. |
| **Reflexión técnica** | Análisis de resultados y aprendizajes. | Reflexión profunda sobre diseño, pruebas y CI/CD. | Reflexión correcta pero superficial. | Reflexión breve o poco argumentada. | Reflexión vaga o sin relación con el taller. | No presenta reflexión. |

> **Cómo suma**: 12 criterios × 5 pts = **60 puntos**.

| Rango de puntaje | Desempeño                                                |
| ---------------- | -------------------------------------------------------- |
| 54 – 60          | Excelente dominio técnico y metodológico.                |
| 42 – 53          | Buen trabajo con documentación o cobertura parcial.      |
| 36 – 41          | Cumple con lo básico pero sin profundidad.               |
| < 36             | No cumple con los criterios mínimos del taller/proyecto. |

---

## 🧭 Propósito del taller

En este taller aplicamos distintas estrategias de **pruebas de integración y sistema** que permiten validar el correcto funcionamiento del software **más allá de las clases individuales**, garantizando la comunicación entre capas, la persistencia de datos y el comportamiento de los endpoints.

A través del caso `Registry`, se aplican los principios de **Testing y Validación de Software** dentro de una **arquitectura limpia**, integrando los componentes de dominio, aplicación, infraestructura y capa de entrega (REST).
El propósito es que los estudiantes comprendan cómo **verificar la interacción entre módulos reales o simulados**, usando herramientas como **H2**, **Mockito** y **Spring Boot Test**, asegurando un flujo confiable de extremo a extremo.

---

## 🧩 Cómo usar esta guía para tu proyecto

1. **Analiza la arquitectura base:** revisa cómo se comunican las capas (`domain`, `application`, `infrastructure`, `delivery`) y cómo se aislan las dependencias.
2. **Ejecuta las pruebas de integración reales (con H2):** valida la persistencia y reglas del dominio con datos reales.
3. **Implementa pruebas con mocks (Mockito):** simula interacciones con repositorios o servicios externos para probar comportamientos aislados.
4. **Agrega pruebas de sistema (HTTP):** verifica los endpoints del controlador (`/register`) con `MockMvc` o `TestRestTemplate`, asegurando respuestas y códigos de estado correctos.
5. **Usa el patrón AAA (Arrange – Act – Assert)** en todas las pruebas para mantener claridad, estructura y trazabilidad.
6. **Documenta el proceso en el Wiki del repositorio**, incluyendo:
   - Descripción del flujo de integración entre capas.
   - Ejemplos de pruebas de integración y mocks.
   - Resultados de pruebas de sistema con evidencias HTTP.
   - Reporte de cobertura (JaCoCo) con análisis de métricas.
   - Conclusiones sobre la importancia de integrar pruebas dentro del ciclo de desarrollo continuo (CI/CD).

---

> 🎯 **Resultado esperado:**
> Al finalizar este taller, cada estudiante o equipo contará con un proyecto con **pruebas de integración y sistema completas**, validando correctamente la interacción entre componentes, con una **cobertura mínima del 80%** y documentación clara que refleje la aplicación práctica de los conceptos de **Testing de Integración, Mockito, Arquitectura Limpia y Pruebas de Sistema (HTTP)**.

---

## Hagamos un resumen

### Pruebas de Integración

- **Qué son:** validan que los diferentes **módulos o capas del sistema funcionen correctamente al interactuar entre sí**.
- **Para qué sirven:** permiten detectar fallos en la comunicación entre componentes (por ejemplo, entre el servicio `Registry` y el repositorio `RegistryRepository`), asegurando que la lógica de negocio se mantenga consistente incluso al persistir o recuperar datos.
- **Ejemplo típico:** usar una base de datos **H2** en memoria para verificar que las inserciones, consultas y restricciones se comportan como se espera.

### Pruebas con Mocks (Mockito)

- **Qué son:** pruebas que **simulan dependencias externas o colaboraciones** (por ejemplo, una base de datos o API externa) para validar la lógica de negocio sin ejecutar código real de infraestructura.
- **Para qué sirven:** permiten aislar el comportamiento de la unidad probada, detectar llamadas inesperadas y asegurar que la integración se produzca bajo las condiciones correctas.
- **Ejemplo típico:** usar `when(...).thenReturn(...)` y `verify(...)` para comprobar que se invoca el método `save()` solo cuando corresponde.

### Pruebas de Sistema (HTTP)

- **Qué son:** verifican el funcionamiento completo del sistema **desde la capa más externa (REST)**, simulando solicitudes reales de usuario a través de endpoints (`POST /register`).
- **Para qué sirven:** prueban el flujo completo: request → capa de aplicación → persistencia → respuesta, validando códigos HTTP (`200`, `400`, `500`) y formatos JSON.
- **Ejemplo típico:** usar `MockMvc` o `TestRestTemplate` para enviar un JSON con los datos de un ciudadano y recibir un resultado como texto (`VALID`, `DUPLICATED`, etc.).

### Arquitectura Limpia en las pruebas

- **Qué es:** una forma de organizar el sistema en capas separadas por responsabilidad:
  - `domain`: contiene las reglas del negocio.
  - `application`: coordina los casos de uso.
  - `infrastructure`: maneja persistencia y comunicación externa.
  - `delivery`: expone el sistema vía REST o interfaz.
- **Para qué sirve:** facilita las pruebas independientes por capa, promueve el desacoplamiento y permite reemplazar implementaciones (por ejemplo, un repositorio real por uno simulado).

---

## Conclusión

En conjunto, estas prácticas permiten:

- Validar la interacción entre componentes (**pruebas de integración**).
- Simular dependencias de forma controlada (**mocks con Mockito**).
- Evaluar el sistema de extremo a extremo (**pruebas HTTP o de sistema**).
- Mantener código modular y verificable (**arquitectura limpia + AAA**).

Con esto se logra **mayor confianza en los despliegues**, **mejor trazabilidad del comportamiento del sistema** y **evidencia sólida del cumplimiento de los requisitos funcionales y no funcionales**.

---

## Recursos recomendados

- *Clean Architecture* – Robert C. Martin
- *Growing Object-Oriented Software, Guided by Tests* – Steve Freeman & Nat Pryce
- Documentación oficial de [Mockito](https://site.mockito.org/)
- [Spring Boot Test Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [JaCoCo Coverage Tool](https://www.jacoco.org/jacoco/)

---

## Créditos y uso académico

**Autor:** César Augusto Vega Fernández
**Curso:** Testing y Validación de Software
**Programa:** Maestría en Ingeniería de Software – Universidad de La Sabana
**Año:** 2025

Este taller y su contenido fueron diseñados por el profesor **César Augusto Vega Fernández** como material académico para el curso *Testing y Validación de Software*, impartido en la **Maestría en Ingeniería de Software de la Universidad de La Sabana**.

Su propósito es exclusivamente educativo y está orientado a fortalecer las competencias de los estudiantes en **pruebas de integración, pruebas de sistema, dobles de prueba y bases de datos reales con Testcontainers** y validación de software en contextos de arquitectura limpia.

---

### Licencia de uso

Este material se distribuye bajo la licencia [Creative Commons Atribución-NoComercial-CompartirIgual 4.0 Internacional (CC BY-NC-SA 4.0)](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.es).

Puedes **usar, adaptar o compartir** este contenido con fines educativos, siempre que:

1. Se reconozca la autoría del profesor **César Augusto Vega Fernández**.
2. No se utilice con fines comerciales.
3. Las obras derivadas se distribuyan bajo la misma licencia.

---

© Universidad de La Sabana – Facultad de Ingeniería
Maestría en Ingeniería de Software – 2025
