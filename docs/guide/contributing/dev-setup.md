# Developer Setup

This page gets you from a fresh clone to running tests.

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java (JDK) | 21 or later | [Adoptium](https://adoptium.net/) recommended. Run `java -version` to check. |
| Maven | 3.8+ | Bundled with most IDEs. Run `mvn -version` to check. |
| GPU | OpenGL 4.1+ | Required for the rendering pipeline. Not needed for headless tests. |
| ROM files | See below | Required for ROM-dependent tests and running the engine. |

An IDE is recommended but not required. The project is developed in IntelliJ IDEA and
includes IntelliJ project files. Any IDE with Maven support will work.

## Clone and Build

```bash
git clone https://github.com/jamesj999/sonic-engine.git
cd sonic-engine
mvn package
```

The build produces an executable OpenGGF JAR with all dependencies at:
```
target/OpenGGF-0.6.prerelease-jar-with-dependencies.jar
```

Maven Silent Extension (MSE) is configured via `.mvn/extensions.xml`. By default, Maven
output is reduced. Use `-Dmse=off` when you need full Maven logs.

## ROM Files

Place ROM files in the project root directory (next to `pom.xml`):

| Game | Filename | Revision |
|------|----------|----------|
| Sonic 1 | `Sonic The Hedgehog (W) (REV01) [!].gen` | World, REV01 |
| Sonic 2 | `Sonic The Hedgehog 2 (W) (REV01) [!].gen` | World, REV01 |
| Sonic 3&K | `Sonic and Knuckles & Sonic 3 (W) [!].gen` | World, lock-on |

ROM files are gitignored. Tests that require ROM data skip gracefully when files are
absent, so you can build and run most tests without any ROMs.

For S3K-specific tests, the ROM path can also be passed as a system property:
```bash
mvn test -Ds3k.rom.path="Sonic and Knuckles & Sonic 3 (W) [!].gen"
```

## Run the Engine

```bash
java -jar target/OpenGGF-0.6.prerelease-jar-with-dependencies.jar
```

On Windows, you can also use the included `run.cmd`.

The engine will open a window showing the master title screen. Select a game with the
arrow keys and press Space. If a ROM file is missing, you will see an error message.

## Run Tests

```bash
# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=TestCollisionLogic

# Run a single test method
mvn test -Dtest=TestCollisionLogic#testSlopeAngle
```

Tests are configured for parallel execution across 8 JVM forks. ROM-dependent tests
(marked with `@RequiresRom` or equivalent guards) skip automatically when ROMs are
absent.

## Project Structure

```
sonic-engine/
  pom.xml                    -- Maven build file
  config.json                -- Runtime configuration (gitignored if custom)
  src/
    main/java/com/openggf/   -- Engine source code
    main/resources/           -- Bundled default config, shaders
    test/java/com/openggf/   -- Test source code
  docs/
    s1disasm/                -- Sonic 1 disassembly (untracked, local reference)
    s2disasm/                -- Sonic 2 disassembly (untracked, local reference)
    skdisasm/                -- Sonic 3&K disassembly (untracked, local reference)
    guide/                   -- This user guide
  tools/                     -- External reference tools
```

For a deeper look at the source layout, see [Architecture](architecture.md).

## GraalVM Native Image (Optional)

The engine supports ahead-of-time compilation via GraalVM native image. This produces
a standalone binary that starts faster and does not require a JVM installation.

To build a native image, you need GraalVM 21+ with the `native-image` tool installed.
The build is configured in `pom.xml` under the `native` profile:

```bash
mvn package -Pnative
```

Native image metadata is maintained in `src/main/resources/META-INF/native-image/`.

## Next Steps

- [Architecture](architecture.md) -- Understand the codebase design
- [Tutorial: Implement an Object](tutorial-implement-object.md) -- Learn by doing
- [Testing](testing.md) -- Writing and running tests
