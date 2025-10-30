# 🔗 Pruebas de Integración y de Sistema

Este taller tiene como objetivo aprender a diseñar, implementar y ejecutar **pruebas de integración** y **pruebas de sistema** en un proyecto Maven.  
A diferencia de las **pruebas unitarias** (que verifican clases de forma aislada), estas validan cómo los **componentes interactúan entre sí** y cómo funciona el sistema **como un todo**.

---

## 1. 📚 Conceptos clave

- **Pruebas de integración**  
  Verifican que los módulos del sistema se comuniquen y trabajen juntos correctamente.  
  Ejemplo: la clase `Registry` (que valida votantes) + `RegistryRepository` (que guarda en la base de datos).

- **Pruebas de sistema**  
  Verifican el comportamiento del software como caja negra, a través de su interfaz pública (ej: endpoints HTTP, CLI).  
  Ejemplo: hacer un `POST /register` y validar la respuesta sin importar la implementación interna.

- **Organizacion del proyecto**
  Verifica los nuevos componeentes en laestrucutra del ejercicio de la registraudria:

main/edu/unisabana/tyvs/registry/
 ├─ domain/
 │   ├─ model/                 # Person, Gender, RegisterResult
 │   └─ service/               # (vacío) o mueve Registry a application
 ├─ application/
 │   ├─ usecase/               # Registry
 │   └─ port/out/              # RegistryRepositoryPort
 ├─ infrastructure/persistence/# RegistryRepository (H2/JDBC), RegistryRecord
 │   ├─ RegistryRecord
 │   └─ RegistryRepository
 └─ delivery/                    # capa de exposición (inbound adapters)
   ├─ rest/                     # HTTP/REST
   │  ├─ RegistryController.java
   │  └─ dto/PersonRequest.java
   ├─ cli/                      # (si algún día hay consola)
   └─ messaging/                # (si algún día hay colas)
test/edu/unisabana/tyvs/registry/
 ├─ application/
 │   ├─ usecase/               # RegistryTest, RegistryWithMockTest
 └─ delivery/                    # capa de exposición (inbound adapters)
     ├─ rest/                     # RegistryControllerIT
---

## 2. ⚙️ Configuración en Maven

Agregamos dependencias y plugins clave al `pom.xml`.

```xml
  <dependencies>
    <!-- JUnit 4 -->
    <dependency>
      <groupId>org.junit.vintage</groupId>
      <artifactId>junit-vintage-engine</artifactId>
      <version>5.10.2</version>
      <scope>test</scope>
    </dependency>

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

    <!-- Tests Spring + JUnit 4/5 (por defecto trae 5; puedes seguir usando 4 si prefieres) -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
      <exclusions>
        <!-- si te quedas con JUnit 4, excluye vintage o ajusta según tu setup -->
      </exclusions>
    </dependency>

    <!-- H2: base de datos en memoria para pruebas de integración -->
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <version>2.2.224</version>
      <scope>test</scope>
    </dependency>

    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <version>1.18.34</version>
    </dependency>

  </dependencies>
```

🔎 **Explicación:**  
- `junit-jupiter`: motor de JUnit 4 → donde están `@Test`, `Assertions`.  
- `mockito-core`: simula dependencias externas, ideal cuando no quieres depender de IO real.  
- `h2`: BD embebida que se crea en memoria para cada prueba → rápida, aislada, no requiere instalación.  

---

## 3. 🏗️ Prueba de Integración con BD H2

Crear el archivo: `edu/unisabana/tyvs/registry/application/usecase/RegistryTest.java`

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
public class RegistryTest {

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

### 🔎 Explicación paso a paso
1. **@BeforeEach → setup()**
   - Configura una BD H2 en memoria (`jdbc:h2:mem:regdb;DB_CLOSE_DELAY=-1`).  
   - Llama a `repo.initSchema()` para crear la tabla de votantes.  
   - Crea un objeto `Registry` que usará ese `repo` real.

2. **Test**
   - Inserta a `p1` → el método `registry.registerVoter(p1)` ejecuta un `INSERT INTO voters(...)`.  
   - Luego se hace una validación directa con `repo.existsById(100)` → consulta a la tabla H2 para confirmar que quedó.  
   - Inserta a `p2` con mismo id → antes de intentar guardar, se hace un `SELECT` en la BD y detecta duplicado, devolviendo `DUPLICATED`.  

👉 Así queda más claro: en la **primera llamada** se hace el insert, y en la **segunda llamada** se valida el duplicado consultando la base de datos.

---

## 4. 🧩 Prueba de Integración con Mockito

Archivo: `src/test/edu/unisabana/tyvs/registry/application/usecase/RegistryWithMockTest.java`

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

🔎 **Explicación:**  
- `mock(RegistryRepositoryPort.class)`: crea un doble de prueba.  
- `when(repo.existsById(777)).thenReturn(true)`: simula que ya existe un votante con id 777.  
- `assertEquals(...)`: validamos que el `Registry` responde `DUPLICATED`.  
- `verify(...)`: asegura que nunca se llamó a `repo.save(...)` → es decir, no intentó grabar un duplicado.  

👉 Aquí no usamos BD real, sino un **mock** para aislar la prueba a la interacción con el repositorio.

---

## 5. 🌐 Prueba de Sistema (caja negra vía HTTP)

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
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RegistryControllerIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    public void shouldRegisterValidPerson() {
        String json = "{\"name\":\"Ana\",\"id\":100,\"age\":30,\"gender\":\"FEMALE\",\"alive\":true}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.postForEntity("/register", new HttpEntity<>(json, headers), String.class);

        assert resp.getStatusCode() == HttpStatus.OK;
        assert "VALID".equals(resp.getBody());
    }
}
```

🔎 **Explicación:**  
- **System under test:** un servidor mínimo que expone `/register`.
- El test **no sabe nada de clases internas** (`Registry`, `Person`) → solo valida que si hago un `POST`, la respuesta es correcta.  
- Esto es lo más parecido a cómo un **cliente real** interactuaría con el sistema.  

---

## 6. 🚀 Ejecución de las pruebas

- Solo unitarias:
  ```bash
  mvn test
  ```

- Unitarias + integración + sistema:
  ```bash
  mvn verify
  ```

Reporte de cobertura combinado con JaCoCo:
```
target/site/jacoco/index.html
```

---

## 7. 📝 Buenas prácticas

1. **Separación clara:** `*Test.java` → unitarias, `*IT.java` → integración/sistema.  
2. **Datos aislados:** usar BD en memoria (H2) evita que las pruebas dependan de un entorno externo.  
3. **Mocks en los límites:** Mockito es útil para pruebas rápidas cuando no quieres depender de IO real.  
4. **Pruebas de sistema = caja negra:** siempre probar por interfaces externas (API, CLI, UI).  

---