# Agent handbook for `jfr-merger`

## Project at a glance
- **Purpose**: Merge one or more Java Flight Recorder captures, generate async-profiler heatmaps (default, CPU-only, allocation-only), and serve them via an HTTP API + static UI.
- **Primary namespaces** live under `src/clj/jfr/`:
  - `core.clj` wires HTTP routes (Ring + http-kit) and lifecycle helpers.
  - `service.clj` handles upload plumbing, merging, invoking async-profiler, and collecting stats.
  - `storage.clj` encapsulates RocksDB persistence for merged artifacts.
  - `utils.clj` hosts helpers shared across the service/storage layers.
- Static assets and config sit in `resources/`; integration assets (e.g., async-profiler jars) go into `lib/` (git-ignored).

## Local setup checklist
1. Install **Java 17+** and the **Clojure CLI tools** (e.g., `brew install clojure/tools/deps` on macOS or follow https://clojure.org/guides/install_clojure for Linux/Windows).
2. Download async-profiler tooling into `lib/` (see commands in `README.md`). `one/convert` expects `lib/jfr-converter.jar` to exist when you run the service or tests.
3. Optional but recommended: create `storage/` with subfolders `jfrs/` and `temp/` if you tweak `resources/config.edn`.
4. **Before running tests**, execute `./prepare-env.sh` to install toolchain prerequisites (rlwrap, Clojure CLI, converters).

### Helpful commands
```bash
# Run the web service (REPL-friendly, port 8080)
clj -M:repl

# Execute the test suite
clj -M:test

# Build and run the uberjar
clj -T:build uber
java -jar target/jfr-merger-0.1.1.jar

# Docker workflow
docker compose up --build
```

## Coding guidelines
- Follow the style you find in existing namespaces: two-space indentation, `:require` lists grouped by project vs. third-party namespaces, and idiomatic `let` bindings (align values for readability only when it does not fight your editor).
- Keep side effects inside `jfr.service`, `jfr.storage`, or dedicated lifecycle helpers—pure helper functions belong in `jfr.utils`.
- Public functions that power HTTP handlers should return Ring-compatible maps (`{:status ... :headers ... :body ...}`) and remain data-oriented.
- Prefer explicit arities over varargs for clarity, and document behavior with docstrings when the side effect is non-obvious (e.g., touching RocksDB or the filesystem).
- When adding dependencies, declare them in `deps.edn` and consider OS-specific classifiers if native libraries are required (mirroring the RocksDB setup).

## Testing & quality gate
- Always run `clj -M:test` before pushing. Tests rely on temporary directories; avoid hard-coding absolute paths.
- If you touch the HTTP layer, hit `http://localhost:8080/index.html` manually or via integration tests to confirm multipart uploads still succeed.
- Ensure any new binaries or large files are ignored via `.gitignore`—only checked-in source/config files should land in commits.

## Operations notes
- Default storage paths (`storage/jfrs`, `storage/temp`) are created automatically at runtime, but production deployments should mount persistent volumes there.
- The HTTP server enforces a 1 GiB upload limit (`:max-body` in `jfr.core/-main`). Document or adjust the limit if your change requires larger recordings.
- RocksDB handles are managed globally; call `jfr.core/stop-server` in REPL sessions to release resources cleanly.

Happy hacking! Keep this file updated when workflows change so newcomers stay productive.
