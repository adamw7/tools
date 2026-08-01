

# tools

Biblioteca de herramientas para diversos fines.

## Table of Contents

- [Validador Maven para archivos de Claude Code](#claude-code-files-maven-enforcer)
- [Generación de código](#code-generation)
- [Ejemplo de gRPC](#grpc-example)
- [Ingeniería de contexto](#context-engineering)
  - [Construcción de contexto para código Java](#java-code-context-build-up)
  - [Construcción de contexto para código Kotlin](#kotlin-code-context-build-up)
  - [Construcción de contexto para código Scala](#scala-code-context-build-up)
  - [Árbol del proyecto](#project-tree)
  - [Formatos de salida](#output-formats)
  - [Contexto con límite de tokens](#token-budget-aware-context)
- [Datos](#data)
  - [Mapa con direccionamiento abierto](#open-addressing-map)
  - [Conjunto con direccionamiento abierto](#open-addressing-set)
  - [Mapa con claves primitivas de tipo int](#primitive-int-keyed-map)
  - [Interruptor de red (kill-switch)](#network-kill-switch)
- [Adopción de Claude Code](#claude-code-adoption)
- [Pruebas de arquitectura (ArchUnit)](#architecture-tests-archunit)
- [Compilación](#building)
- [Lanzamiento de versiones](#releasing)
- [Licencia](#license)

## Claude Code files Maven enforcer

El módulo `claude-code-enforcer` es un conjunto de reglas personalizadas para el [`maven-enforcer-plugin`](https://maven.apache.org/enforcer/maven-enforcer-plugin/) que **fallan la compilación** cuando los archivos de Claude Code del repositorio faltan o están malformados, manteniendo `CLAUDE.md`, `AGENTS.md`, `.claude/settings.json`, los sub-agentes bajo `.claude/agents` y las habilidades bajo `.claude/skills` consistentes y en su formato esperado:

- **`claudeMdFormat`** (`ClaudeMdFormatRule`) — verifica que `CLAUDE.md` exista y no esté vacío, comience con el título `# CLAUDE.md` (se tolera un BOM UTF-8 inicial), haga referencia a `AGENTS.md` y contenga todos los encabezados de sección requeridos.
- **`agentsMdFormat`** (`AgentsMdFormatRule`) — aplica las mismas verificaciones estructurales a `AGENTS.md`: debe comenzar con el título `# AGENTS.md` y contener todos los encabezados de sección requeridos.
- **`skillFilesExist`** (`SkillFilesExistRule`) — verifica que cada directorio de habilidad bajo `.claude/skills` contenga un `SKILL.md` no vacío que se abra con un bloque de front matter YAML que declare todas las claves requeridas (`name`, `description` por defecto). El `name` debe seguir la convención de nomenclatura de Claude Code (kebab-case en minúsculas, longitud acotada) y coincidir con el nombre del directorio de la habilidad; `description` debe ser no vacío y estar dentro de `maxDescriptionLength`. Una lista blanca opcional `allowedFrontMatterKeys` detecta errores tipográficos como `descripton`.
- **`subAgentFormat`** (`SubAgentFormatRule`) — trata cada archivo `*.md` en el directorio de agentes configurado como un sub-agente: debe ser no vacío, abrirse con un bloque de front matter YAML que declare todas las claves requeridas, y llevar un `name` que siga la convención de nomenclatura y coincida con su nombre de archivo. Una lista blanca opcional `allowedModels` rechaza un `model` mal escrito como `claud-opus`.
- **`commandFormat`** (`CommandFormatRule`) — trata cada archivo `*.md` en el directorio de comandos configurado (p. ej. `.claude/commands`) como un comando slash personalizado: debe ser no vacío y llevar un nombre de archivo que siga la convención de nomenclatura de Claude Code, porque el nombre del comando proviene de su nombre de archivo. El front matter es opcional, pero cuando está presente, un `description` debe ser no vacío, un `model` debe ser uno de `allowedModels` cuando esa lista blanca está configurada, y una lista blanca opcional `allowedFrontMatterKeys` detecta errores tipográficos como `argument-hnt`.
- **`settingsJsonValid`** (`SettingsJsonValidRule`) — verifica que `.claude/settings.json` exista, no esté vacío y se analice como JSON. También puede aplicar políticas sobre `permissions.allow`: `requiredPermissions` deben estar todas presentes y `forbiddenPermissions` deben estar todas ausentes, para que un proyecto pueda exigir un permiso del que depende o prohiber un comodín excesivo como `Bash(*)`.
- **`hookCommandsValid`** (`HookCommandsValidRule`) — valida la sección `hooks` de `.claude/settings.json`: cada evento debe mapearse a un array de grupos, cada grupo debe llevar un array `hooks`, y cada hook debe declarar un `type` no en blanco (un hook `command` también requiere un `command` no en blanco). Un comando que apunte a un script local del proyecto — a través de `$CLAUDE_PROJECT_DIR`, o como la ruta relativa al repositorio que Claude Code resuelve de la misma manera — se resuelve contra `projectDir` y debe existir en disco, por lo que un script de hook renombrado o faltante se detecta; solo el programa de cada comando encadenado se lee como un script relativo, por lo que un argumento que parezca una ruta no necesita existir. Una lista blanca opcional `allowedEvents` rechaza un evento mal escrito como `SessionSart`, y `validateScriptReferences` puede desactivar la verificación del script.
- **`mcpServersValid`** (`McpServersValidRule`) — valida el `.mcp.json` del proyecto. Un archivo MCP a nivel de proyecto es opcional, por lo que un archivo ausente pasa la validación; cuando está presente debe ser no vacío y analizarse como JSON, y cada entrada bajo `mcpServers` debe ser un objeto JSON con un transporte bien formado. Un servidor `stdio` (el predeterminado cuando no se declara `type`) necesita un `command` no en blanco; un servidor `sse` o `http` necesita una `url` no en blanco. Un `type` explícito fuera de la lista blanca `allowedTypes` (`stdio`, `sse`, `http` por defecto) se reporta, detectando un error tipográfico como `htttp`. `requiredServers` deben estar todos presentes y `forbiddenServers` deben estar todos ausentes, para que un proyecto pueda exigir un servidor MCP del que depende o prohiber uno que no quiera en el repositorio.
- **`mcpConfigFormat`** (`McpConfigFormatRule`) — valida los detalles de cada entrada de servidor en `.mcp.json` que `mcpServersValid` deja sin verificar: `args` debe ser un array de strings, `env` y `headers` deben ser objetos cuyos valores sean todos strings, una `url` debe ser una URL `http`/`https` sintácticamente válida (y `https` solo cuando `requireHttps` está activado), y un servidor no debe mezclar transportes declarando tanto `command` como `url`. Al igual que `mcpServersValid`, trata un archivo ausente como un paso válido.
- **`hooksFormat`** (`HooksFormatRule`) — valida los scripts de hook bajo un `hooksDir` configurado (p. ej. `.claude/hooks`): cada archivo regular debe ser no vacío, comenzar con un shebang `#!` (`requireShebang`), y llevar el bit ejecutable (`requireExecutable`), y una lista blanca opcional `allowedExtensions` rechaza un archivo suelto. Donde `hookCommandsValid` valida la forma JSON de la sección `hooks`, esta regla valida los scripts en sí mismos; cuando se configura un `settingsFile` también verifica la conexión cruzada, por lo que un hook de comando cuya ruta local del proyecto — con raíz `$CLAUDE_PROJECT_DIR` o relativa al repositorio — caiga en el directorio de hooks debe apuntar a un script que exista allí, y `reportUnreferencedScripts` marca un script que ningún hook referencia. Un `hooksDir` ausente es un paso válido porque los hooks son opcionales.
- **`uniqueDescriptions`** (`UniqueDescriptionsRule`) — lee el `description` del front matter de cada sub-agente (`*.md`), comando (`*.md`) y habilidad (`SKILL.md`) en los `commandsDir`, `agentsDir` y `skillsDir` configurados, y falla cuando una descripción es usada por más de una definición, nombrando cada archivo que la utiliza. Porque Claude enruta haciendo coincidir la intención con estas descripciones, dos descripciones idénticas son ambiguas y una sombra a la otra. La comparación ignora mayúsculas/minúsculas y bloques de espacios en blanco; las descripciones faltantes o en blanco se dejan a las reglas de formato. Al igual que `uniqueNames`, al menos un directorio debe estar configurado y la unicidad se verifica en todos ellos.
- **`uniqueNames`** (`UniqueNamesRule`) — recopila los nombres de cada comando, sub-agente y habilidad desde los `commandsDir`, `agentsDir` y `skillsDir` configurados (el nombre de un comando y un sub-agente es el nombre de su archivo `*.md`, el de una habilidad es el nombre de su directorio) y falla cuando un nombre se usa más de una vez, nombrando cada archivo o directorio que lo utilice. Al menos un directorio debe estar configurado, y cualquier directorio configurado debe existir. La unicidad se verifica en todos los directorios configurados a la vez, por lo que un comando que colisione con una habilidad se detecta igual que dos comandos que colisionan.
- **`crossDocConsistency`** (`CrossDocConsistencyRule`) — evita que `CLAUDE.md` y `AGENTS.md` se contradigan. Cada `consistentPattern` configurado es una expresión regular con un grupo de captura; el valor capturado debe coincidir entre los dos archivos (o estar ausente en ambos). Por ejemplo, `Java (\d+)` falla la compilación si un archivo dice `Java 25` y el otro `Java 24`.
- **`readmeConsistency`** (`ReadmeConsistencyRule`) — evita que este `README.md` se desvíe de la documentación del agente (`AGENTS.md`, la única fuente de verdad). Cada `consistentPattern` configurado (un grupo de captura) debe capturar el mismo valor en ambos archivos, por lo que una capacidad o versión documentada no puede discrepar silenciosamente con la documentación del agente. A diferencia de `crossDocConsistency`, un hecho que el README simplemente no repite se ignora — el README es una vista curada y con muchos ejemplos y puede documentar un subconjunto — por lo que solo un valor presente en ambos archivos que discrepa falla la compilación.

Las reglas `claudeMdFormat` y `agentsMdFormat` comparten una clase base `MarkdownFormatRule` que realiza las verificaciones de existencia de archivo, BOM, título y secciones. También expone verificaciones opcionales, desactivadas por defecto: `forbiddenTokens` que no deben aparecer fuera de vallas de código, `enforceSectionOrder` para exigir las secciones en el orden configurado, un límite `maxLineLength`, y `validateFileReferences` para confirmar que los enlaces Markdown a archivos locales resuelven a algo en disco.

Cada regla extiende una clase base común `ClaudeCodeEnforcerRule` que informa todas las violaciones juntas y respeta una opción `severity`: el predeterminado `error` falla la compilación, mientras que `<severity>warn</severity>` degrada las mismas violaciones a una advertencia registrada para que un equipo pueda adoptar una regla gradualmente.

Un `<reportFile>` opcional escribe el mismo resultado como un informe HTML autocontenido — una sola tabla que empareja qué falló y por qué (el encabezado más una entrada por violación) con los pasos "Cómo solucionar" por regla — para que una compilación pueda mostrar las violaciones en un navegador o como un artefacto de CI. La página está en línea (estilos incluidos, sin recursos externos) para que se abra en cualquier lugar, y se escribe tanto en éxito como en fallo, por lo que un archivo de informe configurado siempre refleja la última ejecución en lugar de dejar un fallo obsoleto:
```xml
<reportFile>${project.build.directory}/claude-code-enforcer.html</reportFile>
```

Las reglas de front matter (`skillFilesExist`, `subAgentFormat`, `commandFormat`) también aceptan una opción `autoFix`. Cuando está habilitada y el front matter de una definición está malformado de una manera segura de reparar — un delimitador escrito con demasiados guiones como `----`, o un `---` de apertura cuyo delimitador de cierre falta — la regla reescribe el archivo en su lugar y continúa contra el contenido corregido en lugar de fallar la compilación. La reparación es conservadora: solo actúa cuando el documento se abre con una línea de guiones que encierra entradas reales `key: value`, por lo que un `---` de separación temática nunca se confunde con front matter. `autoFix` está desactivado por defecto.

Las reglas están conectadas en el `pom.xml` raíz y se ejecutan solo en la raíz del repositorio. La verificación es **opt-in** a través de la propiedad `enforceClaudeMd`, por lo que las compilaciones ordinarias no se ven afectadas:
```
mvn -pl claude-code-enforcer -am install   # instalar el jar de la regla una vez
mvn package -DenforceClaudeMd            # compilar con las verificaciones habilitadas
```


## Code generation

Problema:

El código Java generado de constructores para protobuf detecta campos obligatorios faltantes en tiempo de ejecución.

Solución:

Mover la detección al tiempo de compilación (shift-left). Para un recorrido visual de *cómo* la cadena de interfaces generadas garantiza que cada campo obligatorio se establezca antes de que se pueda llamar a `build()`, consulte
[docs/compile-time-safe-builders.md](docs/compile-time-safe-builders.md).

Ejemplo del problema:
```proto
syntax = "proto2";

package example;

option java_multiple_files = true;
option java_package = "io.github.adamw7.tools.code.protos";

message Person {
  optional string name = 1;
  required int32 id = 2;
  optional string email = 3;
  required string department = 4;
}
```
y el constructor que permite construir el objeto sin establecer el campo obligatorio "Id":
```java
Person.Builder personBuilder = Person.newBuilder();

personBuilder.setEmail("email@sth.com");
personBuilder.setName("Adam");

UninitializedMessageException thrown = assertThrows(UninitializedMessageException.class, personBuilder::build, "Expected build method to throw, but it didn't");

assertEquals("Message missing required fields: id, department", thrown.getMessage());
```
Solución:
```xml
<plugin>
	<groupId>io.github.adamw7</groupId>
	<artifactId>protogen-maven-plugin</artifactId>
	<!-- Use the latest release: https://github.com/adamw7/tools/releases/latest -->
	<version>2.4.0</version>
	<configuration>
		<generatedSourcesDir>${project.basedir}/target/generated-sources/</generatedSourcesDir>
		<pkgs>
			<param>io.github.adamw7.tools.code.protos</param>
		</pkgs>
		<outputpackage>io.github.adamw7.tools.code.builders</outputpackage>
	</configuration>
	<executions>
		<execution>
			<phase>generate-sources</phase>
			<goals>
				<goal>code-generator</goal>
			</goals>
		</execution>
	</executions>
</plugin>
```
que genera constructores que detectan campos obligatorios faltantes en tiempo de compilación (algunos métodos se omiten por simplicidad del ejemplo):
```java
interface OptionalIfc {
	OptionalIfc setEmail(String email);
	OptionalIfc setName(String name);
	Person build();
}

interface DepartmentIfc {
	OptionalIfc setDepartment(String department);
}

interface IdIfc {
	DepartmentIfc setId(int id);
}

class OptionalImpl implements OptionalIfc {
	
	private final Builder builder;

	public OptionalImpl(Builder builder) {
		this.builder = builder;
	}

	@Override
	public OptionalIfc setEmail(String email) {
		builder.setEmail(email);
		return this;
	}

	@Override
	public OptionalIfc setName(String name) {
		builder.setName(name);
		return this;
	}

	@Override
	public Person build() {
		return builder.build();
	}
}

class DepartmentImpl implements DepartmentIfc {

	private final Builder personOrBuilder;

	public DepartmentImpl(Builder personOrBuilder) {
		this.personOrBuilder = personOrBuilder;
	}

	@Override
	public OptionalIfc setDepartment(String department) {
		personOrBuilder.setDepartment(department);
		return new OptionalImpl(personOrBuilder);
	}	
}

public class ExampleTest {
	
	private static class PersonBuilderExample implements IdIfc {
		private final Builder personBuilder = Person.newBuilder();
		
		@Override
		public DepartmentIfc setId(int id) {
			personBuilder.setId(id);
			return new DepartmentImpl(personBuilder);
		}
	}
	
	@Test
	public void happyPath() {
		PersonBuilderExample builder = new PersonBuilderExample();
		Person person = builder.setId(1).setDepartment("dep").setEmail("sth@sth.net").setName("Adam").build();
		assertEquals(1, person.getId());
		assertEquals("dep", person.getDepartment());
		assertEquals("sth@sth.net", person.getEmail());
		assertEquals("Adam", person.getName());
		
	}
}
```
Se admiten tanto proto2 como proto3. En proto2, el constructor generado exige que cada campo `required` se establezca antes de que se pueda llamar a `build()`. proto3 no tiene campos `required`, por lo que no hay nada que hacer cumplir allí; el constructor simplemente expone todos los campos como opcionales. El seguimiento de presencia se maneja correctamente para cada sintaxis: se genera un accesorio `hasXxx()` solo para campos que realmente rastrean presencia — cada campo singular en proto2, pero en proto3 solo campos de mensaje y aquellos declarados con la palabra clave `optional` explícita (los escalares de proto3 con presencia implícita, que no tienen `hasXxx()`, se dejan solos).

Un grupo `oneof` obtiene adicionalmente un accesorio `getXxxCase()` que devuelve el enum `XxxCase` generado por protobuf, para que pueda saber qué miembro está configurado, más un `clearXxx()` que reinicia todo el grupo — ambos accesibles a través de la cadena del constructor fluido. Los oneofs sintéticos que respaldan los campos `optional` de proto3 no se tratan como grupos, por lo que no se genera un accesorio de caso spurious para ellos.

## gRPC example

Un ejemplo de gRPC de extremo a extremo que combina la generación de código protobuf/gRPC estándar con la generación de constructores seguros en tiempo de compilación de este proyecto.

Dado [`greeter.proto`](grpc-example/src/main/proto/greeter.proto):
```proto
syntax = "proto2";

message HelloRequest {
  required string name = 1;
  optional string title = 2;
}

message HelloReply {
  required string message = 1;
}

service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}
```

Dos generadores se ejecutan durante la compilación:
1. **`protobuf-maven-plugin`** compila las definiciones proto en clases de mensajes protobuf y stubs de servicio gRPC (`GreeterGrpc`).
2. **`protogen-maven-plugin`** (este repositorio) genera constructores seguros en tiempo de compilación (`HelloRequestBuilder`, `HelloReplyBuilder`) que se niegan a llamar a `build()` hasta que cada campo `required` esté configurado.

La implementación del servicio usa el constructor generado:
```java
HelloReply reply = new HelloReplyBuilder().setMessage(greetingFor(request)).build();
```

> **Nota:** Todo el código de ejemplo (`GreeterServiceImpl`, `GreeterServer`, `GreeterClient`) vive bajo `src/test/java` porque los constructores generados por protogen se escriben en `target/generated-test-sources`. Ejecute el ejemplo con `mvn -pl grpc-example -am test`.

Consulte el [módulo grpc-example](grpc-example/README.md) para obtener todos los detalles y cómo ejecutar el ejemplo.

## Context engineering

### Java code context build up

Para los agentes de IA generativa que trabajan con código Java, el contexto suele comenzar con una clase, pero puede ampliarse y extenderse a las clases que la utilizan, y así sucesivamente.
Para construir este árbol, existe una interfaz basada en expresiones regulares muy simple y rápida:

```java
public interface Context {
    Set<ClassContainer> find(ClassContainer root, int depth);
}
```
donde `ClassContainer` contiene la ruta de la clase y sus fuentes.
El parámetro `depth` indica al buscador qué tan profundo queremos ir en el árbol de usos de la clase.
Por supuesto, cuando la profundidad crece, el árbol crece muy rápido.

El `Finder` basado en expresiones regulares es la implementación predeterminada. Elija el idioma con
el enum `Language` (`Language.JAVA` es el predeterminado):

```java
Context context = new Finder(allContainers, Language.JAVA);
Set<ClassContainer> used = context.find(root, depth);
```

`Language.JAVA` resuelve archivos `.java` al mapear un nombre de clase referenciado de vuelta
a su archivo fuente, por lo que la construcción del árbol de usos funciona fuera de la caja para fuentes
Java.

### Kotlin code context build up

Kotlin se soporta con exactamente las mismas características que Java. El
`Finder` basado en expresiones regulares y la interfaz `Context` son agnósticos al idioma; la única diferencia
es la extensión del archivo fuente utilizada para resolver una clase referenciada de vuelta a un archivo.
Elija el idioma con el enum `Language`:

```java
Context context = new Finder(allContainers, Language.KOTLIN);
Set<ClassContainer> used = context.find(root, depth);
```

`Language.JAVA` (el predeterminado) resuelve archivos `.java` y `Language.KOTLIN`
resuelve archivos `.kt`, por lo que la misma construcción de árbol de usos funciona para fuentes Kotlin.

### Scala code context build up

Scala se soporta con exactamente las mismas características que Java y Kotlin. La
interfaz `Context` y el `Finder` basado en expresiones regulares son agnósticos al idioma; la única
diferencia es la extensión del archivo fuente utilizada para resolver una clase referenciada de vuelta a un archivo. Elija el idioma con el enum `Language`:

```java
Context context = new Finder(allContainers, Language.SCALA);
Set<ClassContainer> used = context.find(root, depth);
```

`Language.SCALA` resuelve archivos `.scala`, por lo que la misma construcción de árbol de usos funciona
para fuentes Scala.

### Project tree

Una sola clase rara vez es suficiente contexto: un agente suele necesitar ver cómo
se organiza un proyecto completo y cómo se relacionan sus archivos. `ProjectTreeBuilder` recorre un
directorio de proyecto Java (o Kotlin o Scala) y produce un árbol de sus **carpetas, archivos
y dependencias** en una sola estructura:

```java
ProjectTreeNode root = new ProjectTreeBuilder(/* depth */ 1).build(Path.of("my-project"));
System.out.println(new ProjectTreePrinter().print(root));
```

Para un proyecto Kotlin simplemente pase el idioma; todo lo demás permanece igual:

```java
ProjectTreeNode root = new ProjectTreeBuilder(Language.KOTLIN, /* depth */ 1).build(Path.of("my-project"));
System.out.println(new ProjectTreePrinter().print(root));
```

Los bloques de construcción son:

- **`ProjectTreeNode`** — un nodo en el árbol. Cada nodo es o bien un *directorio*
  (que refleja una carpeta del proyecto y contiene nodos hijos) o un *archivo*. Los nodos de archivo
  llevan adicionalmente el conjunto de clases del proyecto de las que dependen, por lo que carpetas,
  archivos y dependencias están todos descritos por el mismo árbol.
- **`ProjectTreeBuilder`** — escanea el directorio del proyecto recursivamente. Cada
  carpeta se convierte en un nodo de directorio y cada archivo en un nodo de archivo (los directorios
  se listan antes que los archivos, luego alfabéticamente). Para cada archivo fuente (`.java`,
  `.kt` o `.scala`, dependiendo del `Language` configurado) resuelve las
  clases del proyecto que usa con el mismo `Context` basado en expresiones regulares, omitiendo
  la propia clase del archivo.
  `depth` controla qué tan profunda es la búsqueda de uso, exactamente como en la interfaz `Context`
  anterior.
- **`ContextFactory`** — desacopla el constructor del buscador de dependencias concreto (por defecto `Finder`), por lo que puede conectarse una estrategia de resolución diferente sin cambiar el constructor.
- **`ProjectTreePrinter`** — renderiza el árbol como texto indentado, con las dependencias de cada archivo
  listadas debajo:

```
[dir] pkg
  [file] A.java
  [file] B.java
    -> A.java
```

El resultado es una vista compacta, legible para humanos y para LLM, del proyecto, lista para
ser entregada a un agente de IA generativa como contexto.

### Output formats

El árbol puede renderizarse en varios formatos detrás de una sola interfaz
`ProjectTreeSerializer`, por lo que un consumidor depende de la abstracción
en lugar de un formato concreto y pueden agregarse nuevos formatos sin tocar el
árbol:

```java
ProjectTreeSerializer serializer = new ProjectTreeMarkdownSerializer(); // o JSON / printer
String rendered = serializer.serialize(root);
```

- **`ProjectTreePrinter`** — texto plano indentado (mostrado arriba).
- **`ProjectTreeMarkdownSerializer`** — una lista de viñetas Markdown anidada, con las dependencias de cada archivo como viñetas hijas indentadas. Ideal para documentos y agentes basados en chat.
- **`ProjectTreeJsonSerializer`** — JSON estructurado (`name`, `type`,
  `dependencies`, `children`) para consumidores programáticos; `serializePretty`
  produce JSON indentado.
- **`ProjectTreeDotSerializer`** — un digrafo Graphviz [DOT](https://graphviz.org/doc/info/lang.html)
  de las aristas de dependencia (archivo → clase de la que depende); los directorios añaden
  estructura pero no se dibujan. Renderícelo con herramientas de Graphviz.
- **`ProjectTreeMermaidSerializer`** — las mismas aristas de dependencia como un
  `flowchart` de [Mermaid](https://mermaid.js.org/syntax/flowchart.html), que
  se renderiza en línea en GitHub, en visores de Markdown y en muchas superficies de agentes de IA generativa
  sin ninguna herramienta externa.

### Token-budget-aware context

La ventana de contexto de un modelo es finita, por lo que `BudgetedContext` envuelve cualquier `Context` y
recorta su resultado para que se ajuste a un presupuesto de tokens. Porque `Finder` devuelve dependencias en
orden de ancho primero (más cercanas primero), el decorador mantiene ese orden de prioridad y
acepta contenedores hasta que el siguiente excedería el presupuesto:

```java
TokenEstimator estimator = new HeuristicTokenEstimator(); // ~chars/4, sin dependencia de tokenizer
Context budgeted = new BudgetedContext(new Finder(allContainers), estimator, /* token budget */ 8000);
Set<ClassContainer> used = budgeted.find(root, depth);
```

- **`TokenEstimator`** — abstrae cómo se calcula el costo en tokens de un fragmento de texto.
- **`HeuristicTokenEstimator`** — una estimación rápida y sin dependencias a partir del conteo de caracteres
  (caracteres por token configurables, por defecto `4`), redondeada hacia arriba para que cualquier
  texto no vacío cueste al menos un token.
- **`BudgetedContext`** — un decorador `Context` que devuelve el prefijo de mayor prioridad
  del grafo de dependencias que cabe en el presupuesto.

## Data
Contiene:
- fuentes de datos
  - soporte para carga de datos relacionales
  - carga en memoria e iterativa
  - soporte para CSV, JDBC
  - Parquet (`InMemoryParquetDataSource`, `IterableParquetDataSource`) — lectura a través de un motor DuckDB en proceso, exponiendo las columnas y filas del archivo como cualquier otra fuente respaldada por JDBC
  - JSON (`InMemoryJSONDataSource`, `IterableJSONDataSource`) — los objetos anidados se aplanan con claves de ruta punteada (p. ej. `people[0].address.city`)
  - YAML (`InMemoryYAMLDataSource`, `IterableYAMLDataSource`) — misma convención de aplanamiento; sin límite de tamaño de documento
  - TOON (`InMemoryTOONDataSource`, `IterableTOONDataSource`) — un formato compacto y amigable para LLM que minimiza los tokens; soporta pares clave-valor, arrays primitivos, arrays tabulares y objetos anidados
  - Todas las fuentes basadas en archivos aceptan una ruta de archivo o un `InputStream`
  - Descompresión GZIP: cualquier fuente basada en archivos descomprime automáticamente archivos `.gz`; no se necesita configuración adicional
- herramienta de verificación de unicidad
  - para un conjunto de datos dado y un subconjunto de columnas, puede preguntar si esas columnas son únicas (pueden usarse como clave)
  - la herramienta también intenta encontrar una respuesta mejor (más pequeña)
  - soporta procesamiento en memoria e iterativo
- estructuras de datos
  - mapa hash con direccionamiento abierto: una alternativa más simple a HashMap basada solo en un array y doble hash, implementa java.util.Map<K, V>
- servidor MCP
  - Servidor del Protocolo de Contexto de Modelo (MCP) que expone la verificación de unicidad como una herramienta para asistentes de IA
  - Compatible con Claude Desktop, Cline y otros clientes MCP
  - Transportes (seleccione con `--transport.mode`):
    - `stdio` (predeterminado) — JSON-RPC sobre stdin/stdout (Spring Boot, sin iniciar servidor HTTP)
    - `streamable-http` — el transporte HTTP moderno servido en `/mcp`
    - `stateless-http` — el mismo transporte HTTP servido en `/mcp`, pero sin sesión: cada solicitud JSON-RPC se responde de forma aislada, lo que conviene a despliegues balanceados o sin servidor
  - Compilación: `mvn clean install` produce `data/target/tools.data-<version>.jar`
  - Ejecución: `java -jar data/target/tools.data-<version>.jar --transport.mode=stdio`
  - Consulte la [Documentación de Uso de MCP](data/src/main/java/io/github/adamw7/tools/data/uniqueness/mcp/MCP_USAGE.md) para la configuración del cliente (Claude Desktop, Cline) y ejemplos de uso
  
Ejemplos:

verificación en memoria:
```java
		AbstractUniqueness check = new InMemoryUniquenessCheck();
		check.setDataSource(new InMemorySQLDataSource(connection, query));
		Result result = check.exec("COLUMN1", "COLUMN2", "COLUMN3");
		log.info(result.isUnique());
		Set<Result> betterOptions = result.getBetterOptions();
		for (Result betterOption : betterOptions) {
			log.info(betterOption);	
		}
```
Para agregar una nueva fuente de datos, por ejemplo para XML, JSON, etc., solo necesita implementar esta interfaz:
```java
public interface IterableDataSource extends AutoCloseable, Closeable {
	public String[] getColumnNames();
	
	public void open();
	
	public String[] nextRow();

	public boolean hasMoreData();
	
	public void reset();

	// método por defecto, carga hasta batchSize filas en una operación
	public List<String[]> nextRows(int batchSize);
}
```
`nextRows(int batchSize)` permite a los llamadores decidir cuántos datos se extraen de la fuente a la vez en lugar de leer fila por fila. Es un método por defecto construido sobre `hasMoreData()`/`nextRow()`, por lo que cada fuente lo obtiene gratis; una lista vacía señala que la fuente se agotó. La fuente SQL aplica adicionalmente `batchSize` como el tamaño de obtención de JDBC para que las filas se obtengan en una sola ida y vuelta.
Si necesita una fuente en memoria, necesita implementar un método adicional:
```java
public interface InMemoryDataSource extends IterableDataSource {
	public List<String[]> readAll();
}
```

Notas:

Las verificaciones en memoria usan fuentes en memoria que cargan todos los datos una vez y ejecutan múltiples verificaciones recursivas para encontrar mejores opciones.
Las verificaciones iterativas (sin memoria) mantienen solo una fila a la vez, por lo que requieren un tamaño de heap muy pequeño, pero para las verificaciones recursivas necesitan leer la fuente muchas veces. 

### Open-addressing map

`OpenAddressingMap<K, V>` es una implementación de `java.util.Map` que es **más simple
que `java.util.HashMap` porque usa solo un array**: las entradas se almacenan
directamente en un único array mediante direccionamiento abierto, en lugar de los arrays de
buckets con nodos enlazados (o arbolizados) de `HashMap`. Esto lo convierte en una alternativa
con pocas asignaciones cuando desea un mapa plano sin los objetos de nodo por entrada de la
encadenamiento separado.

```java
Map<String, Integer> map = new OpenAddressingMap<>(); // capacidad por defecto 64
map.put("a", 1);
map.put("b", 2);
map.get("a");          // 1
map.remove("b");       // 2
map.containsKey("b");  // false
```

Cómo funciona:

- **Doble hash** resuelve colisiones: la secuencia de sondas de una clave es
  `h1 + i * h2` (módulo la longitud del array), que dispersa las sondas mejor que
  la sondeo lineal y evita el clustering primario. `h1`/`h2` se derivan de
  `hashCode()` de la clave y un primo elegido como el mayor primo menor que la
  longitud del array.
- **Tumbstones para la eliminación**: `remove` marca una ranura como eliminada en lugar
  de limpiarla, para que las secuencias de sondas que pasaron *por* esa ranura aún encuentren las
  entradas colocadas después. `put` reutiliza la primera ranura libre y una ranura nunca usada
  (`null`) termina una búsqueda.
- **Redimensionamiento automático**: cuando el array está a punto de llenarse, crece por un
  factor de `1.2` y todas las entradas vivas se vuelven a hashear en el nuevo array (los tumbstones
  se descartan en el proceso). La capacidad inicial puede configurarse mediante
  `new OpenAddressingMap<>(size)` (tamaño efectivo mínimo es 3); un
  tamaño no positivo se rechaza con `IllegalArgumentException`.

Precauciones:

- **No se admiten claves nulas** — `put`/`get` con una clave `null` lanzan
  `IllegalArgumentException`.
- **Los valores nulos no son distinguibles de la ausencia**: `get` devuelve `null`
  para una clave faltante y `containsKey` se define como `get(key) != null`, por lo que una clave
  mapeada a un valor `null` se reporta como ausente. Evite almacenar valores `null`.
- **No es seguro para subprocesos**; guarde la sincronización externa si se comparte entre
  subprocesos.

### Open-addressing set

`OpenAddressingSet<E>` es un `java.util.Set` respaldado por un `OpenAddressingMap`, de
la misma manera que `java.util.HashSet` está respaldado por un `java.util.HashMap`.
Los elementos se almacenan como claves del mapa subyacente contra un valor centinela
compartido, por lo que todo el comportamiento de direccionamiento abierto (doble hash,
eliminación con tumbstones y redimensionamiento automático) se **reutiliza en lugar de reimplementarse**.

```java
Set<String> set = new OpenAddressingSet<>(); // capacidad por defecto 64
set.add("a");          // true  (recién agregado)
set.add("a");          // false (ya existente)
set.contains("a");     // true
set.remove("a");       // true
```

Hereda las precauciones del mapa: **no se admiten elementos nulos** (se
rechazan con `IllegalArgumentException`) y **no es seguro para subprocesos**. La
capacidad inicial puede configurarse mediante `new OpenAddressingSet<>(size)`.

### Primitive int-keyed map

`IntKeyOpenAddressingMap<V>` es un hermano con claves primitivas `int` de
`OpenAddressingMap`. Usa la misma estrategia de direccionamiento abierto con doble hash,
pero almacena las claves en un `int[]` para que las búsquedas e inserciones **nunca empaqueten la clave**.
Eso lo convierte en una opción con pocas asignaciones para mapas grandes con claves enteras donde el
autoempaque de un `Map<Integer, V>` dominaría de otra manera.

```java
IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
map.put(1, "a");
map.get(1);              // "a"
map.getOrDefault(2, ""); // ""  (ausente)
map.remove(1);           // "a"
int[] keys = map.keys(); // claves vivas, sin empaquetar
```

Deliberadamente **no** implementa `java.util.Map`, porque esa interfaz está
definida en términos de claves `Object` y reintroduciría el mismo empaquetamiento que esta
clase existe para evitar; en su lugar, refleja las operaciones relevantes del mapa con
claves `int` primitivas. A diferencia de `OpenAddressingMap`, **los valores `null` se almacenan
fielmente** y se reportan por `containsKey(int)` — solo `get(int)` no puede distinguir un
`null` almacenado de una clave ausente. **No es seguro para subprocesos**.

### Network kill-switch

`Switch` **desactiva todo el acceso de red saliente de la JVM** durante la vida del
proceso. Es útil cuando desea garantizar que una ejecución de procesamiento de datos se mantenga
sin conexión — p. ej., ninguna llamada accidental externa mientras se carga y verifica datos locales.

```java
boolean changed = Switch.off(); // true la primera vez, false si ya está desactivado
```

`Switch.off()` instala un `ProxySelector` predeterminado que rechaza cada selección de proxy
lanzando `UnsupportedOperationException("The network is off")`, por lo que
cualquier intento posterior de abrir una conexión saliente falla rápido. El método es:

- **De un solo sentido** — no hay `on()`; una vez desactivado, la JVM se mantiene sin conexión. Aplíquelo
  temprano, solo cuando realmente quiera sellar el proceso.
- **Idempotente y seguro para subprocesos** — está `synchronized` y protegido por una
  bandera `volatile`; llamarlo de nuevo es un no-op que registra una advertencia y devuelve
  `false`.
  
El interruptor está conectado a la ejecución de pruebas unitarias del módulo `data` por exactamente esta
razón: un `NetworkOffExtension` (un `BeforeAllCallback` de JUnit descubierto a través
de `META-INF/services` y la auto-detección de extensiones de JUnit) llama a `Switch.off()`
antes de que se ejecute cualquier prueba unitaria, por lo que una prueba unitaria nunca puede abrir una conexión saliente.
La auto-detección y la propiedad de guardia `tools.test.network.off` se configuran solo en
surefire, por lo que las pruebas de integración failsafe (`*IT`), que necesitan red real, lo mantienen. Consulte *Testing* en [AGENTS.md](AGENTS.md) para los detalles.

Una sola prueba también puede optarse explícitamente con la anotación `@NetworkOff`, que
registra la misma extensión a través de `@ExtendWith`:

```java
@NetworkOff
class MyDataSourceTest {
    // cada prueba aquí se ejecuta con la red desactivada
}
```

A diferencia de la auto-detección a nivel de módulo, un `@NetworkOff` explícito activa el
interruptor incondicionalmente — independientemente de la propiedad `tools.test.network.off`
— por lo que la red está desactivada incluso cuando la prueba se ejecuta por sí sola desde un IDE.

## Claude Code adoption

El módulo `adopt` (`tools.adopt`) es una pipeline ordenada que **adopta Claude
Code en un repositorio de GitHub**. Dada una URL de repositorio, clona el repo,
crea una rama de características, ejecuta el CLI de Claude Code (`claude -p /init`) para
generar un `CLAUDE.md` y lo confirma, luego conecta una guardia de `CLAUDE.md` en la
compilación del proyecto y confirma eso también — para que el `CLAUDE.md` recién generado siga
siendo validado en cada compilación. La guardia es consciente de la herramienta de compilación: un proyecto Maven
obtiene la regla completa [`claude-code-enforcer`](#claude-code-files-maven-enforcer) en
su `pom.xml`, mientras que un proyecto Gradle (Groovy `build.gradle` o Kotlin
`build.gradle.kts`) obtiene una tarea de guardia de presencia y no vacío anexada al
script de compilación — Gradle no tiene un equivalente de regla enforcer. Un repositorio sin
un archivo de compilación reconocido vuelve a un flujo de trabajo de GitHub Actions agnóstico a la herramienta que ejecuta un script portátil de presencia y no vacío en cada push
y pull request, por lo que incluso un repositorio sin compilación mantiene la guardia. Finalmente empuja la
rama y abre un pull request con el CLI de GitHub, para que el cambio sea revisado
en lugar de aterrizar directamente en la rama predeterminada, a la que nunca se escribe.

Ejecute desde la línea de comandos con una URL de repositorio de GitHub, un directorio de trabajo opcional
para clonar (se crea un directorio temporal cuando se omite),
y un nombre de rama de características opcional (por defecto `claude/adopt-claude-code`).
Porque abre el pull request a través del CLI de GitHub, un `gh` autenticado
debe estar en el `PATH` junto a `git` y `claude`. Inícielo a través de `exec:java`
para que Maven coloque el classpath de tiempo de ejecución completo (log4j2 y el resto) en el comando — un
`java -cp adopt/target/classes` sin más omite los jars de dependencia y falla en
el inicio con un `NoClassDefFoundError` para el `LogManager` de log4j:

```bash
mvn -pl adopt exec:java \
    -Dexec.args="https://github.com/owner/repo.git [workspace-directory] [branch-name]"
```

`--help` (o `-h`) imprime la línea de uso y no adopta nada, por lo que los argumentos pueden
preguntarse sin nombrar un repositorio.

Antes de dejar que una ejecución escriba en GitHub, `--dry-run` la ensaya: el repositorio se
clona, ramifica y confirma, y la guardia se conecta y verifica, pero
la rama nunca se empuja y no se abre ningún pull request. La pipeline se
ensambla *sin* esos dos pasos en lugar de con pasos que deciden no hacer
nada, por lo que el informe `completedSteps` termina en `verify` y dice lo que realmente
ocurrió. El checkout se deja en el espacio de trabajo para que los commits de la adopción sean leídos
antes de publicar nada:

```bash
mvn -pl adopt exec:java \
    -Dexec.args="https://github.com/owner/repo.git --workspace /tmp/adoptions --dry-run"
```

Cada comando externo está acotado por un timeout — 10 minutos por defecto —
anulable con `--timeout <minutes>`, para un repositorio cuyo `claude init` o
cuya primera compilación Maven contra un `~/.m2` frío necesita más tiempo, o para un lote que
debería fallar rápido en lugar de tardarse.

Una sola ejecución puede adoptar **una lista de repositorios** en lugar de uno solo: repita
`--repo <url>` para cada uno, o apunte `--repos <file>` a un archivo que nombre un
repositorio por línea (las líneas en blanco se omiten y una línea `#` es un comentario, por lo que un
lote puede anotarse y un repositorio comentarse para una ejecución). Los duplicados se
adoptan una vez. Cada repositorio de la ejecución comparte el espacio de trabajo y el nombre de la rama — cada clon aterriza en su propio directorio bajo el espacio de trabajo, nombrado después del
repositorio — por lo que un lote impulsado enteramente por los flags los nombra con
`--workspace` y `--branch`, siendo el primer argumento posicional siempre una
URL de repositorio; un primer argumento posicional que no nombre un dueño de repositorio mientras los flags
nombraron repositorios se rechaza, ya que ese es el espacio de trabajo que esta lectura invita.
Dos repositorios que clonarían en el mismo directorio — `owner/tools` y
`other-owner/tools`, o un repositorio nombrado tanto con como sin su sufijo `.git`
— se rechazan antes de que se clone algo en lugar de adoptarse uno sobre el otro:

```bash
mvn -pl adopt exec:java \
    -Dexec.args="--repos repos.txt --workspace /tmp/adoptions --report report.json"
```

Un repositorio cuya adopción falla no varastra los que vienen después: el lote
se ejecuta hasta el final, y los fallos se elevan juntos después para que el comando
salga no cero. El archivo `--report` dice qué repositorios aterrizaron — una ejecución
sobre varios repositorios escribe un `succeeded` global (verdadero solo cuando todos
fueron adoptados) y un array `repositories` de exactamente los documentos por repositorio que una
ejecución de un solo repositorio escribe sin envolver:

```json
{
  "succeeded" : false,
  "repositories" : [ {
    "repositoryUrl" : "https://github.com/owner/repo.git",
    "branch" : "claude/adopt-claude-code",
    "pullRequestUrl" : "https://github.com/owner/repo/pull/42",
    "succeeded" : true,
    "failure" : null,
    "completedSteps" : [ "toolchain", "clone", "…", "pull-request" ]
  }, {
    "repositoryUrl" : "https://github.com/owner/other.git",
    "branch" : "claude/adopt-claude-code",
    "pullRequestUrl" : null,
    "succeeded" : false,
    "failure" : "clone: repository not found",
    "completedSteps" : [ "toolchain" ]
  } ]
}
```

La versión de `claude-code-enforcer` de la que un `pom.xml` de proyecto Maven debe depender
por defecto es la versión de la compilación `tools` que ejecuta la adopción;
`--rule-version <version>` fija una diferente. Un `-SNAPSHOT` se rechaza de cualquier manera, porque solo se resuelve desde el repositorio local de la máquina que adopta:
conectar uno abriría un pull request que compila para quien ejecutó la adopción
y falla para la CI del proyecto adoptado y cada uno de sus colaboradores. Por lo tanto, una compilación snapshot de `tools` no puede adoptar un proyecto Maven hasta que se publique una versión de la regla.

La pipeline es una lista de `AdoptionStep`s ordenados e independientes, cada uno actuando sobre un
`AdoptionContext` inmutable compartido (la URL del repositorio, el espacio de trabajo, el
directorio de checkout derivado y el nombre de la rama de características):

```java
AdoptionOptions options = AdoptionOptions.defaults();
CommandRunner runner = new ProcessCommandRunner(options.commandTimeout());
GitHubRepoAdopter.withDefaultPipeline(runner, options)
    .adopt(new AdoptionContext("https://github.com/owner/repo.git", workspace), new AdoptionReport());
```

`AdoptionOptions` es cómo se configura una ejecución — los metadatos del pull request, los
activos iniciales, la versión de la regla a fijar, si es una ejecución en seco, y cuánto tiempo
puede durar un comando. Ambos puntos de entrada construyen uno, por lo que la línea de comandos y la
herramienta MCP no pueden divergir sobre lo que significa una opción omitida, y la fábrica de pipeline
no crece con un parámetro por switch.

El informe es un parámetro en lugar de un solo valor de retorno, por lo que una ejecución que falla
a mitad de camino aún deja al llamador con los pasos que se completaron y la
razón por la que se detuvo.

`BatchAdoption` envuelve esa pipeline para trabajar a través de una lista de repositorios, un
`AdoptionContext` a la vez, y responde con un `AdoptionRun` (el contexto y
su informe) por repositorio — un repositorio fallido se registra en lugar de
permitir abandonar el resto:

```java
GitHubRepoAdopter adopter = GitHubRepoAdopter.withDefaultPipeline(runner, AdoptionOptions.defaults());
List<AdoptionRun> runs = new BatchAdoption(adopter::adopt).adoptAll(contexts);
```

La pipeline predeterminada ejecuta estos pasos en orden:

1. **`ToolchainStep`** — prueba las herramientas externas a las que la pipeline hace shell
   (`git`, `claude`, `gh`) con una verificación `--version` antes de cualquier trabajo real, para que
   una herramienta faltante aborte la adopción inmediatamente con un mensaje que nombre cada
   ausente en lugar de fallar minutos después de un clon, un `claude init`,
   y una compilación Maven ya hayan ejecutado. Instalar no es suficiente para `gh`:
   `gh --version` tiene éxito para un CLI en el que nadie está conectado, por lo que el login
   se prueba también y un `gh` no autenticado falla aquí en lugar de en el último
   paso. La prueba lee el repositorio que se está adoptando, `gh api repos/<owner>/<repo>`
   — el acceso que el pull request necesitará — en lugar de `gh auth status`, que
   informa un `GH_TOKEN` rechazado como inválido y aún sale con cero, y en lugar de
   `gh api user` con alcance de usuario, que un token de instalación de GitHub App (un `GITHUB_TOKEN` de CI) rechaza incluso cuando puede abrir el pull request
   perfectamente bien. Una URL que no nombre un dueño no tiene repositorio para preguntar y vuelve
   a `gh api user`.
2. **`CloneStep`** — clona el repositorio objetivo en el espacio de trabajo con
   `git clone`, dando a los pasos restantes un checkout de trabajo.
3. **`BuildToolchainStep`** — prueba la herramienta de compilación *del proyecto adoptado*, ahora que
   el clon ha revelado cuál es. `ToolchainStep` no puede: el checkout
   aún no existe. Sin esto, una máquina sin `mvn` o
   `gradle` del proyecto solo falla en `VerifyStep`, después de un `claude init` completo, un `CLAUDE.md` remodelado, y dos commits ya hayan sido gastados en un checkout que nunca iba a ser verificable. Un sistema de compilación que no necesita su propia herramienta — la guardia de fallback se ejecuta a través de `sh` — es un no-op.
4. **`BranchStep`** — crea y checkea la rama de características de adopción con
   `git checkout -B`, para que cada commit posterior aterrice en esa rama en lugar de la rama predeterminada. Un clon fresco cuyo `origin` ya publica la rama — una
   readopción de un repositorio que una ejecución anterior empujó — la inicia desde esa
   punta publicada, para que el empuje posterior permanezca como un fast-forward en lugar de ser
   rechazado; una rama que ya existe localmente se deja sola, para que el trabajo no empujado
   en un espacio de trabajo reutilizado nunca se resetee en el remoto.
5. **`TrustStep`** — marca el checkout como confiable en `~/.claude.json` para que
   la ejecución headless de `claude` no se bloquee por el prompt interactivo de confianza de carpeta.
6. **`ClaudeInitStep`** — ejecuta el CLI de Claude Code en modo headless
   (`claude -p /init` por defecto; la invocación es configurable porque los
   flags difieren entre entornos) para que genere un `CLAUDE.md`, abortando si
   el archivo no apareció.
7. **`CommitStep`** — confirma el `CLAUDE.md` generado (`Adopt Claude Code: add
   CLAUDE.md`).
8. **`EnforcerStep`** — detecta el sistema de compilación del checkout y conecta la
   guardia de `CLAUDE.md` en él. Un proyecto Maven tiene el `claude-code-enforcer` agregado
   a su `pom.xml` raíz a través de `PomEnforcerInstaller` (la edición se hace en el DOM del JDK
   — sin biblioteca XML de terceros — es consciente de espacios de nombres, e es idempotente); un
   proyecto Gradle tiene una tarea de guardia `enforceClaudeMd` anexada a su
   `build.gradle`/`build.gradle.kts` a través de `GradleGuardInstaller`, conectada en
   `check`. Un repositorio sin un archivo de compilación reconocido vuelve a un
   `FallbackBuildSystem` que instala un flujo de trabajo de GitHub Actions y el portátil
   `.github/claude-md-guard.sh` que ejecuta a través de `WorkflowGuardInstaller`. Todas
   las instalaciones son idempotentes y ninguna sobrescribe un archivo que el proyecto ya
   lleva — un repositorio con su propio `claude-md-guard.yml` o su propia
   registro `enforceClaudeMd` lo mantiene. Soportar una nueva herramienta de compilación es un
   asunto de agregar una implementación `BuildSystem` en lugar de bifurcar dentro
   del paso. Para Maven, esa verificación ya declarada abarca todo el POM en lugar de
   solo sus `build/plugins`: un proyecto que ejecuta la regla detrás de un perfil opt-in,
   o la declara en `pluginManagement`, se deja solo en lugar de darle una
   segunda copia siempre activa. La edición del POM se splicing en los bytes que el archivo ya
   contiene, para que el commit de adopción muestre el bloque agregado y nada más — escribir
   el DOM editado completo normalizaría detalles que un DOM no registra,
   colapsando una etiqueta de inicio extendida sobre varias líneas y reescribiendo `<rule />` como
   `<rule/>`, convirtiendo una adición de catorce líneas en un diff a través del archivo.
9. **`CommitStep`** — confirma el cambio de compilación (`Add claude-code-enforcer to the
   build`).
10. **`VerifyStep`** — ejecuta la verificación del sistema de compilación detectado (un
    `mvn -N validate` no recursivo para Maven, la tarea `enforceClaudeMd` para
    Gradle, el script `.github/claude-md-guard.sh` para el fallback) para que
    la guardia recién conectada ejecute realmente contra el `CLAUDE.md` generado,
    fallando la adopción localmente si el archivo falta o está malformado en lugar de
    después de que aterrice el pull request.
11. **`PushStep`** — empuja la rama de características a origin y establece su upstream
    (`git push -u origin <branch>`). Dejado fuera de una pipeline `--dry-run`, junto con
    el paso debajo.
12. **`PullRequestStep`** — abre un pull request desde la rama con
    `gh pr create`, apuntando a la rama predeterminada del repositorio como base. Los
    metadatos del pull request se suministran a través de `PullRequestOptions` — título, cuerpo,
    y revisores opcionales, etiquetas y asignatarios para solicitar, más si abrir
    el pull request como un `--draft` — para que los valores predeterminados puedan anularse por
    proyecto. Al igual que `CommitStep` permanece idempotente: los pull requests abiertos de la rama
    se leen primero (`gh pr list --state open`) y la creación se omite cuando uno ya
    está abierto, y un `gh pr create` que falla solo porque un pull request para
    la rama ya existe, o porque no hay commits entre base y
    head, se trata como un no-op en lugar de abortar la adopción. Esa tolerancia es
    lo que hace seguro volver a ejecutar una adopción incluso donde la preverificación no puede ejecutarse: `gh
    pr list` necesita una consulta que un token restringido o un host proxyado pueda rechazar, y una
    consulta fallida es indistinguible de "nada está abierto" — por lo que se registra como una
    advertencia y el create que sigue se permite reportar el duplicado en sí.

Las invocaciones externas de `git`/`claude`/`gh` pasan por una abstracción `CommandRunner`,
para que los pasos se prueben unitariamente sin generar procesos reales. El
`ProcessCommandRunner` predeterminado fusiona el error estándar en la salida estándar para una sola
transcripción ordenada, y **acota cada comando con un timeout configurable**
(10 minutos por defecto, `--timeout <minutes>` en la línea de comandos y
`timeout_minutes` en la herramienta MCP) para que un clon estancado o un `claude` atascado
no puedan congelar la adopción — al expirar, el proceso hijo se destruye y el fallo se
informa con lo que haya capturado hasta ese momento. Un paso cuyo comando sale
no cero aborta la pipeline con un `AdoptionException` que lleva la transcripción del comando.

## Architecture tests (ArchUnit)

Cada módulo de producción guarda su propia estructura de paquetes con
reglas de [ArchUnit](https://www.archunit.org/) que se ejecutan como pruebas ordinarias de JUnit 5,
para que una dependencia accidental o una convención de nomenclatura rota **falla la compilación**
en lugar de erosionar silenciosamente el diseño. Cada módulo mantiene sus reglas en un solo
`*ArchitectureTest` bajo un paquete `architecture`, anotado con
`@AnalyzeClasses(..., importOptions = ImportOption.DoNotIncludeTests.class)` para
que solo se analicen clases de producción. La dependencia de ArchUnit
(`com.tngtech.archunit:archunit-junit5`) se gestiona centralmente en el
`pom.xml` raíz, y las pruebas se ejecutan como parte del `mvn install` normal.

Un conjunto de convenciones se comparte entre módulos:

- **Sin ciclos entre paquetes** — `slices().matching(...).should().beFreeOfCycles()`
  mantiene el grafo de paquetes acíclico en cada módulo.
- **Los loggers son constantes** — cada campo `org.apache.logging.log4j.Logger` debe
  ser `private static final` (el módulo de contexto relaja esto a `private final`),
  porque un logger es un colaborador compartido, inmutable y de alcance de clase.
- **Los tipos `*Exception` realmente son excepciones** — cualquier clase cuyo nombre simple termine
  con `Exception` debe ser asignable a `java.lang.Exception`.
- **Nombres con prefijo `Abstract`** — una clase abstracta de nivel superior debe tener un nombre simple
  que comience con `Abstract`, para que un tipo que deba ser extendido sea obvio
  de un vistazo.
- **El logging pasa por log4j2, no por la consola ni el JDK** — `GeneralCodingRules` de ArchUnit
  prohíben el acceso a `System.out`/`System.err`, lanzar
  excepciones genéricas y usar `java.util.logging`; las bibliotecas adicionalmente no deben
  llamar nunca a `System.exit`. El módulo `data` endurece esto aún más, también
  rechazando el propio `System.Logger` del JDK para que todo el logging se mantenga en log4j2.
- **Sin rastros de pila crudos** — ninguna clase puede llamar a ninguna sobrecarga de `Throwable.printStackTrace`,
  porque un fallo debe reportarse a través de log4j2 en lugar de volcarse
  a la consola.
- **Los campos públicos son inmutables** — cada campo `public` debe ser `final`, para que un
  campo que es parte de la superficie API de un tipo no pueda reasignarse desde fuera.
- **Sin biblioteca de fechas heredada** — `GeneralCodingRules` prohíben una dependencia de
  Joda-Time, manteniendo el manejo de fecha/hora en la API `java.time`.

Encima de esa línea base, cada módulo fija los límites específicos de su propio
diseño:

- **[`data`](data/src/test/java/io/github/adamw7/tools/data/architecture/DataArchitectureTest.java)** — los contratos de fuente de datos (`source.interfaces`) deben permanecer interfaces
  y no deben conocer sus implementaciones concretas `source.db`/`source.file`;
  `structure.internal` es accesible solo desde `structure`; las colecciones reutilizables
  `structure` no deben acoplarse a fuentes de datos; cada
  `*DataSource` concreto debe implementar `IterableDataSource`; el núcleo de unicidad no debe
  depender de su adaptador `mcp`; y una `layeredArchitecture` fija las capas de fuente
  para que las fuentes de archivo y BD dependan solo hacia abajo de sus contratos (los archivos también
  pueden usar compresión), nunca entre sí. Dos reglas mantienen las estructuras de direccionamiento abierto
  honestas sobre su falta documentada de seguridad de subprocesos: ningún método en
  `structure` puede ser `synchronized`, y `structure` no puede depender de
  `java.util.concurrent`, para que ninguno pueda sugerir silenciosamente que las colecciones son seguras
  para compartir. Dos más fijan el interruptor `network` a su forma documentada:
  `Switch.off()` debe ser `synchronized` y su bandera `isOff` debe ser `volatile`.
- **[`code/context`](code/context/src/test/java/io/github/adamw7/context/architecture/ContextArchitectureTest.java)** — el núcleo finder/tree no debe depender del paquete de entrega `mcp`,
  y solo ese paquete `mcp` puede construirse sobre el andamaje MCP compartido; cada
  `*Serializer` concreto debe honrar el
  contrato `ProjectTreeSerializer`.
- **[`code/protogen-maven-plugin`](code/protogen-maven-plugin/src/test/java/io/github/adamw7/tools/code/architecture/ProtogenArchitectureTest.java)** — el paquete reutilizable `format` no debe
  depender del generador de código `gen` que se construye sobre él, y cada
  `*Mojo` concreto debe implementar el contrato Maven `Mojo`.
- **[`mcp-common`](mcp-common/src/test/java/io/github/adamw7/tools/mcp/architecture/McpCommonArchitectureTest.java)** — el SPI `McpTool` debe permanecer una interfaz, cada
  `*Tool` concreto debe implementarla, y el andamaje compartido nunca debe llamar
  a `System.exit`.
- **[`claude-code-enforcer`](claude-code-enforcer/src/test/java/io/github/adamw7/tools/enforcer/architecture/EnforcerArchitectureTest.java)** — una `layeredArchitecture` fija las capas del módulo
  (`text` es la base, `rule` se construye sobre ella, y los paquetes de características
  `definition`/`doc`/`mcp`/`settings` se construyen sobre `rule` sin alcanzar lateralmente
  unos a otros), y cada `*Rule` concreto debe extender la
  clase base compartida `ClaudeCodeEnforcerRule`.
- **[`adopt`](adopt/src/test/java/io/github/adamw7/tools/adopt/architecture/AdoptArchitectureTest.java)** — la capa de ejecución de comandos `command` no debe depender del
  paquete `step`, para que la abstracción de comando reutilizable permanezca inconsciente de los
  pasos de adopción que se construyen sobre ella; y cada `*Step` concreto en `step` debe
  implementar el contrato `AdoptionStep`. La pipeline también lleva la línea base
  compartida completa, incluyendo que informa fallos lanzando
  `AdoptionException` y nunca llama a `System.exit`.

Junto con las reglas de producción, cada módulo lleva una compañera
`TestConventionsArchitectureTest` que analiza solo las clases de *prueba* (vía
`ImportOption.OnlyIncludeTests`) y fija convenciones sobre las pruebas en sí:
cada método `@Testable` debe vivir en una clase `*Test` o `*IT` para que surefire o
failsafe realmente lo ejecute, ninguna prueba es `@Disabled`, las pruebas usan solo JUnit 5 (sin
API de JUnit 4 `org.junit`), y ninguna prueba llama a `Thread.sleep` o `TimeUnit.sleep`
(dormir es lento e inestable — espere una condición en lugar). Unas pocas reglas protegen
contra pruebas que silenciosamente nunca se ejecutan: un método `@Testable` no debe ser
`private` ni `static` (JUnit 5 ignora silenciosamente ambos), y un método `@BeforeAll`/
`@AfterAll` debe ser `static` (JUnit 5 lo requiere a menos que la clase opte
por el ciclo de vida `PER_CLASS`).

Ejecute para un solo módulo con, por ejemplo:
```
mvn -pl data -am test
```
(`-am` es requerido — un `mvn -pl data test` sin más falla la regla enforcer
`ReactorModuleConvergence` del pom raíz.)
o en todo el repositorio como parte de `mvn install`.

# Building
```
mvn clean install
```
La parte clean es necesaria ya que la compilación contiene generación de código, por lo que si elimina una fuente de generación y no usa clean, el resultado de la compilación anterior puede permanecer en target. Si no elimina nada, podría beneficiarse de una compilación más rápida:
```
mvn install
```
# Releasing
Para lanzar una nueva versión - X necesita:
1. Cambiar la propiedad revision a X en root pom.xml
2. Confirmar y empujar
3. Verificar si todas las compilaciones pasan
4. Lanzar y marcar como más reciente en GitHub

# License

Este proyecto está licenciado bajo la [Licencia MIT](LICENSE).
