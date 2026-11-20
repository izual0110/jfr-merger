# Agent guide for `jfr-merger`

`jfr-merger` merges Java Flight Recorder (JFR) recordings, generates HTML visualizations, and serves them through an HTTP API and browser UI. See [README.md](README.md) for setup, commands, and API usage.

## Where to work

Under `src/clj/jfr/`:

- `core.clj`: Ring routes, multipart middleware, and Aleph server lifecycle.
- `service.clj`: JFR merging, async-profiler conversion, statistics, and history.
- `storage.clj`: RocksDB persistence and lifecycle.
- `heapdump.clj`: heap dump analysis with JOL.
- `detector/`: background pattern detection and report rendering.
- `environ.clj` and `utils.clj`: configuration access and shared helpers.

Static assets are in `resources/public/`, configuration is in `resources/config.edn`, and tests are in `test/`.

## Change rules

- Inspect the relevant code and tests before editing. Make focused changes and preserve unrelated work.
- Follow nearby Clojure formatting: two-space indentation, grouped `:require` entries, and idiomatic `let` bindings. Prefer explicit arities and use docstrings for non-obvious side effects.
- Keep HTTP handlers data-oriented and return Ring response maps. Put filesystem and database operations in the relevant service or storage namespace; keep pure shared helpers in `jfr.utils`.
- Reuse existing dependencies. Ask before adding one, declare it in `deps.edn`, and check platform support for native libraries.
- Use temporary directories in tests. Do not commit recordings, heap dumps, generated binaries, or local storage. Update `.gitignore` if a workflow produces new local artifacts.

## Verification

- Run the tests that cover the change and run `clj -M:test` before pushing. If the CLI is missing on Linux, review `prepare-env.sh` before running it: it can install system packages. See [README.md](README.md) for prerequisites.
- For HTTP changes, verify the affected routes. For upload changes, open `http://localhost:8080/index.html` with the default configuration and submit a multipart upload, or cover the equivalent flow in an integration test.
- In REPL sessions, call `(jfr.core/stop-server)` to stop the server and detector worker and release RocksDB resources.
- Report the checks you ran and any checks you could not run. Keep this guide and the README current when workflows change.
