# Taller de Pruebas de Integración y Sistema

Este taller tiene como objetivo aprender a diseñar, implementar y ejecutar **pruebas de integración** y **pruebas de sistema** en un proyecto Maven.  
En el flujo de desarrollo de software, a diferencia de las **pruebas unitarias** (que verifican clases de forma aislada), las pruebas de integración y sistema permiten verificar cómo los **componentes interactúan entre sí** y cómo funciona el sistema **como un todo**.

---

## 🎯 Objetivo General

Comprender, diseñar e implementar **pruebas de integración y de sistema** sobre una aplicación con **arquitectura limpia**, usando herramientas como **JUnit 4/5**, **Mockito**, **H2** y **Spring Boot Test**.

---

## 📑 Índice

- [PRUEBAS DE INTEGRACIÓN BÁSICAS](#pruebas-de-integración-básicas)
- [Prueba de Integración con BD H2](#prueba-de-integración-con-bd-h2)
- [Pruebas de Integración con Mocks](#pruebas-de-integración-con-mocks)
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

## COMOCE EL TALLER

### Estructura del Proyecto

Verifica los nuevos componentes en la estrucutra del ejercicio de la registraduría:

```gherkin
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
```

---

### Configuración de Dependencias

Agregamos dependencias y plugins clave al `pom.xml`.

```xml
  <dependencies>
    <!-- JUnit 5 -->
    <dependency>
      <groupId>org.junit.vintage</groupId>
      <artifactId>junit-vintage-engine</artifactId>
      <version>5.10.2</version>
      <scope>test</scope>
    </dependency>

    <!-- JUnit 4 -->
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

    <!-- Tests Spring + JUnit 4/5 -->
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

**Explicación:**  

- `junit-jupiter`: corresponde al motor de **JUnit 5**, que incluye las anotaciones principales como `@Test`, `@BeforeEach`, `@AfterEach` y la clase `Assertions`.
En este proyecto se utiliza **JUnit 4** como base, pero también se integra **JUnit 5** (Jupiter) para la ejecución de pruebas más especializadas o con nuevas características del framework, como el soporte para pruebas parametrizadas o mayor compatibilidad con **Spring Boot Test**.
- `mockito-core`: simula dependencias externas, ideal cuando no quieres depender de IO real.  
- `h2`: BD embebida que se crea en memoria para cada prueba → rápida, aislada, no requiere instalación.  

---

## PRUEBAS DE INTEGRACIÓN BÁSICAS

---

### Prueba de Integración con BD H2

Las pruebas de integración evalúan la **interacción entre múltiples módulos o capas**.  
En este taller, se probará la relación entre el **caso de uso `Registry`** y el **adaptador `RegistryRepository`** (que usa una BD en memoria H2).

#### Ejemplo Base: `RegistryTest`

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

#### Explicación paso a paso

1. **@BeforeEach → setup()**
   - Configura una BD H2 en memoria (`jdbc:h2:mem:regdb;DB_CLOSE_DELAY=-1`).  
   - Llama a `repo.initSchema()` para crear la tabla de votantes.  
   - Crea un objeto `Registry` que usará ese `repo` real.

2. **Test**
   - Inserta a `p1` → el método `registry.registerVoter(p1)` ejecuta un `INSERT INTO voters(...)`.  
   - Luego se hace una validación directa con `repo.existsById(100)` → consulta a la tabla H2 para confirmar que quedó.  
   - Inserta a `p2` con mismo id → antes de intentar guardar, se hace un `SELECT` en la BD y detecta duplicado, devolviendo `DUPLICATED`.  

👉 Así queda más claro: en la **primera llamada** se hace el insert, y en la **segunda llamada** se valida el duplicado consultando la base de datos.

#### Actividades con el uso de BD H2

1. Implementa pruebas para los siguientes casos:
   - Persona duplicada (`DUPLICATED`)
   - Menor de edad (`UNDERAGE`)
   - Persona fallecida (`DEAD`)
   - ID inválido (`INVALID`)
2. Aplica el formato **AAA (Arrange – Act – Assert)** en cada test.
3. Añade aserciones que verifiquen la persistencia real con H2.

#### 💡 Reto adicional con el uso de BD H2

Simula un error de conexión en H2 y observa cómo responde tu caso de uso.

---

### Pruebas de Integración con Mocks

Cuando no se desea usar una base de datos real, podemos **simular el repositorio** con Mockito.

#### Ejemplo Base: `RegistryWithMockTest`

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;

// src/test/java/.../RegistryControllerIT.java
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RegistryControllerIT {

    @TestConfiguration
    static class TestBeans {
        @Bean
        public RegistryRepositoryPort registryRepositoryPort() throws Exception {
            String jdbc = "jdbc:h2:mem:regdb;DB_CLOSE_DELAY=-1";
            var repo = new edu.unisabana.tyvs.registry.infrastructure.persistence.RegistryRepository(jdbc);
            repo.initSchema();
            return repo;
        }

        @Bean
        public edu.unisabana.tyvs.registry.application.usecase.Registry registry(RegistryRepositoryPort port) {
            return new edu.unisabana.tyvs.registry.application.usecase.Registry(port);
        }
    }

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

#### Explicación del Test de sistemas

- **System under test:** un servidor mínimo que expone `/register`.
- El test **no sabe nada de clases internas** (`Registry`, `Person`) → solo valida que si hago un `POST`, la respuesta es correcta.  
- Esto es lo más parecido a cómo un **cliente real** interactuaría con el sistema.  

#### Actividades con Sistemas

1. Realiza pruebas con distintos cuerpos JSON que produzcan los estados `VALID`, `DUPLICATED`, `UNDERAGE`, `DEAD`.  
2. Usa Postman o curl para verificar los endpoints `/register` y documenta tus observaciones.  
3. Implementa una prueba negativa (JSON incompleto o tipo incorrecto).

#### 💡 Reto adicional con Sistemas

Agrega validaciones con `@Valid` en el `PersonDTO` y prueba que el sistema devuelva errores HTTP adecuados (`400`, `409`, `422`).

---

### Ejecución de las pruebas

- Solo unitarias:

```bash
mvn test
```

- Unitarias + integración + sistema:

```bash
mvn verify
```

Reporte de cobertura combinado con JaCoCo:

```gherkin
target/site/jacoco/index.html
```

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

- Al menos **3 pruebas con base de datos H2** cubriendo interacciones reales entre `Registry` y `RegistryRepository`.  
- Casos mínimos:
  - Persona válida → `VALID`  
  - Persona duplicada → `DUPLICATED`  
  - Persona menor de edad → `UNDERAGE`  
  - Persona fallecida → `DEAD`  
- Deben ejecutarse sin mocks, verificando que los datos se persisten realmente.  
- Usa formato **AAA (Arrange – Act – Assert)** y nombres descriptivos (`shouldReturnDuplicatedWhenIdExists()`).

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
- Validar los endpoints reales (`/register`) devolviendo respuestas HTTP correctas (`200`, `400`, `500`).  
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

| Caso | Entrada | Resultado Esperado | Tipo | Test |
|------|----------|--------------------|------|------|
| Persona duplicada | ID=101 existente | `DUPLICATED` | H2 | `shouldReturnDuplicatedWhenExists()` |
| Persona válida | ID=200, edad=25 | `VALID` | HTTP | `shouldRegisterValidPerson()` |

### 8) Gestión de defectos

- Archivo **`defectos.md`** con al menos **1 defecto real o simulado** detectado por pruebas de integración o sistema.  
  - **Caso probado**  
  - **Resultado esperado vs. obtenido**  
  - **Causa probable**  
  - **Estado:** Abierto / Cerrado  
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
| **Pruebas de sistema (HTTP)** | Validación de endpoints reales con MockMvc o RestTemplate. | ≥2 pruebas HTTP completas (200, 400, 500), con aserciones válidas. | Pruebas funcionales pero con casos limitados. | Pruebas incompletas o con endpoints incorrectos. | Pruebas fallidas o sin conexión al servidor. | No existen pruebas HTTP. |
| **Cobertura de pruebas (JaCoCo)** | Nivel de cobertura global y por capa. | ≥80% global y ≥70% en `application` y `delivery`. | Entre 70–79% global, sin grandes omisiones. | Cobertura media (50–69%) o irregular. | Cobertura <50%. | No presenta reporte o no genera cobertura. |
| **Matriz de pruebas** | Tabla de casos probados y correspondencia con métodos de test. | Matriz completa, clara y actualizada. | Matriz parcial con algunos casos omitidos. | Matriz incompleta o sin correspondencia con código. | Matriz confusa o sin formato. | No entrega matriz. |
| **Gestión de defectos** | Registro de defectos y análisis. | Documento `defectos.md` con al menos 1 caso bien analizado. | Documento con casos simulados pero comprensibles. | Documento incompleto o superficial. | Caso sin análisis o sin evidencias. | No entrega `defectos.md`. |
| **Calidad del código** | Claridad, limpieza y consistencia del código. | Código limpio, sin duplicaciones, constantes extraídas, buen uso de excepciones. | Código comprensible con leves redundancias. | Código con duplicación o nombres poco claros. | Código confuso o sin buenas prácticas. | Código desorganizado o con errores graves. |
| **Reflexión técnica** | Análisis de resultados y aprendizajes. | Reflexión profunda sobre diseño, pruebas y CI/CD. | Reflexión correcta pero superficial. | Reflexión breve o poco argumentada. | Reflexión vaga o sin relación con el taller. | No presenta reflexión. |

| Rango de puntaje | Desempeño                                                |
| ---------------- | -------------------------------------------------------- |
| 45 – 50          | Excelente dominio técnico y metodológico.                |
| 35 – 44          | Buen trabajo con documentación o cobertura parcial.      |
| 30 – 34          | Cumple con lo básico pero sin profundidad.               |
| < 30             | No cumple con los criterios mínimos del taller/proyecto. |

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

Su propósito es exclusivamente educativo y está orientado a fortalecer las competencias de los estudiantes en **TDD, AAA, Clases de Equivalencia, BDD** y validación de software en contextos de arquitectura limpia.

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
