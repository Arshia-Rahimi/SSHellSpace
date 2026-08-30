# AGENTS.md

SSHellSpace: a Kotlin/JVM desktop SSH client (Compose Multiplatform for Desktop) with terminal, vault (credentials), settings, and tabbed UI. Pure JVM — no KMP targets: sources in `src/main/kotlin`, tests in `src/test/kotlin`.

## Build & commands

- `./gradlew run` (launch app), `build`, `check` (tests + detekt), `clean`.
- Per-module: `./gradlew :feature:vault:ui:test` / `:core:data:check`.
- JDK toolchain **25** is required everywhere (`jvmToolchain(25)`); auto-provisioned by the foojay resolver in `settings.gradle.kts`.
- `gradle.properties` enables the build cache **and** configuration cache — keep build-logic changes configuration-cache compatible.
- `./gradlew graphUpdate` regenerates each module's `DependencyGraph.md` (Mermaid; tasks come from `project.root`).

## Module layout & conventions

- Composite build `build-logic/` defines convention plugins: `project.root`, `project.kotlin.library`, `project.compose.library`, `project.sqldelight`. New modules apply a convention plugin instead of hand-wiring plugins/deps — they auto-add coroutines, Koin, kotlinx-serialization, detekt, and test deps (JUnit4, mockk, kotlin-test, coroutines-test).
- Clean architecture: `core/{common,data,domain,tabs,ui}` and `feature/{vault,terminal,settings,newtab}/{data,domain,ui}`. `app/` is the assembly root (Koin wiring, driver, packaging).
- Type-safe accessors are enabled — reference modules as `projects.feature.vault.ui`, never string paths.
- `detekt` is applied to every JVM module via convention with default config (no `detekt.yml`).

## DI (Koin 4 + Koin Compiler plugin)

- Hand-written `module { }` blocks using the mutation DSL from `org.koin.plugin.module.dsl` (`single<T>()`, `viewModel<T>()`, `create(::fn)`), plus `bind` for interface bindings. No annotation style in this codebase.
- Each module exposes a `XxxModule` val; **every** module must also be added to `includes(...)` in `AppModule` (`app/src/main/kotlin/com/sshellspace/KoinInit.kt`).

## SQLDelight (generated code)

- `.sq` files live under each module's `src/main/sqldelight/`; their generated code is produced at build time.
- `project.sqldelight` convention sets the package to `com.shellspace.<path-with-dots>.generated` (e.g. `com.shellspace.core.data.generated`). `app` uses the raw plugin with package `com.shellspace.generated`. Import the generated `Database` from that package.
- SQLite DB is stored in the OS user-data dir (appdirs, `SSHellSpace/sshellspace.db`); schema comes from `Database.Schema` and `foreign_keys=true` is set.

## Tabs

- `core/tabs`: `Tab` (owns a `ViewModelStore`), `TabItem`, and `TabManager` (`TabManagerImpl` lives in `app`).
- Register tabs in `app/src/main/kotlin/com/sshellspace/config/Tabs.kt` (`fixedTabItems`) and inspect `NewTabDialogEvent` to add new-tab flows.

## Terminal & packaging

- Terminal emulators sit behind the `Terminal` interface (`feature/terminal/ui/.../screen/emulators/Terminal.kt`); bossterm is the concrete emulator, with `BossTermSshProcessHandleAdapter` bridging Apache MINA sshd (ssh-common/sftp/scp) for remote sessions; pty4j handles local PTYs.
- Packaging uses the nucleus plugin: `nucleusApplication(backend = NucleusBackend.Tao)`, target formats Dmg/Msi/AppImage, GraalVM native-image enabled, main class `com.sshellspace.MainKt`.

## Gotchas

- No tests exist yet; the test dependencies are already wired in by `project.kotlin.library`.