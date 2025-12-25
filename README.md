# jfr-merger

`jfr-merger` is a lightweight web application that helps you explore Java Flight Recorder (JFR) profiles. Upload one or more `.jfr` files, merge them, and instantly inspect the resulting CPU and allocation heatmaps inside the browser.

> **Why another profiler tool?**
> When you're dealing with production systems, you often end up with several short-lived JFR recordings gathered from different nodes. `jfr-merger` stitches those captures together, generates async-profiler heatmaps, and stores the artifacts so that you can iterate quickly.

---

## Features

- **Merge multiple JFR captures** into a single profile before generating visualizations.
- **Generate three heatmaps per upload** – an aggregated view, CPU-only, and allocation-only heatmaps.
- **Persist results in RocksDB** so you can share URLs or compare uploads later without reprocessing.
- **Expose REST endpoints** for automation-friendly workflows (fetch stats, download heatmaps, list stored recordings).
- **Docker-friendly packaging** and an uberjar build for easy deployment to your preferred environment.

---

## Getting started

### Prerequisites

- Java 17 or newer (required by async-profiler tooling).
- [Clojure CLI tools](https://clojure.org/guides/getting_started) for local development.
- `curl` and `tar` for fetching async-profiler artifacts.

### 1. Clone and bootstrap dependencies

```bash
cd /path/to/workspace
git clone https://github.com/izual0110/jfr-merger.git
cd jfr-merger

# Download the async-profiler converter jars used during heatmap generation
mkdir -p lib
curl -L -o lib/jfr-converter.jar https://github.com/async-profiler/async-profiler/releases/download/v4.2.1/jfr-converter.jar
curl -fsSL "https://github.com/async-profiler/async-profiler/releases/download/v4.2.1/async-profiler-4.2.1-linux-x64.tar.gz" | tar -xz -C lib
```

### 2. Configure storage paths (optional)

By default, the application writes processed heatmaps to `storage/jfrs` and temporary merged files to `storage/temp`. Adjust these paths by editing [`resources/config.edn`](resources/config.edn):

```clojure
{:jfr-data-path "storage/jfrs"
 :temp-dir     "storage/temp"}
```

> The configured `:jfr-data-path` must be readable and writable by the running process. During development the directories are created automatically.

### 3. Run the application locally

```bash
# Start a development REPL (includes the HTTP server on port 8080)
clj -M:repl

# tests
clj -M:test

#run service
clj -X:uberjar

# or build and run the executable uberjar
clj -T:build uber
java -jar target/jfr-merger-0.1.1.jar
```

Once the server is running, open [http://localhost:8080/index.html](http://localhost:8080/index.html) in your browser.

### 4. Docker workflow

The repository ships with a `Dockerfile` and `docker-compose.yml` for containerized deployments:

```bash
# Build the runtime image (downloads the async-profiler artifacts during build)
docker build -t jfr-merger .

# Run the container, exposing the HTTP server on port 8080
docker run --rm -p 8080:8080 jfr-merger

# For interactive debugging
docker run --rm -it --entrypoint bash jfr-merger

# Drop into a running container (replace `jfr-merger` with the container name)
docker exec -it jfr-merger /bin/bash

# For remove everything
docker system prune -a
```

To orchestrate alongside other services, use Docker Compose:

```bash
docker compose up --build
```

---

## Using the UI

1. Navigate to the upload page and drop one or more `.jfr` files.
2. Click **Merge & Generate**. The server streams each file into a temporary merged recording, then runs async-profiler's `jfr-converter` in three modes (default, `--cpu`, `--alloc`).
3. When processing finishes you'll see a link to the generated heatmap plus a summary block with:
   - total event count
   - recording window (UTC start/end)
   - duration in milliseconds
   - frequency of event types encountered
4. Switch between the stored heatmaps or share the link with your teammates. Artifacts are cached in RocksDB and remain available until you clear the database.

### Generating test data

If you need synthetic load, the repository includes [`test/BadPatternsDemo.java`](test/BadPatternsDemo.java):

```bash
java -agentpath:$(pwd)/lib/async-profiler-4.1-linux-x64/lib/libasyncProfiler.so=start,event=cpu,alloc,file=profile.jfr test/BadPatternsDemo.java
```

Upload `profile.jfr` through the UI to experiment with the heatmaps. To preview a flamegraph locally without the web app, run
`java -jar lib/jfr-converter.jar profile.jfr profile.html` and open the generated HTML file in a browser.

---

## API reference

While the SPA is the primary entry point, the server exposes a small REST interface (useful for scripting or building your own frontend):

| Method | Path                    | Description |
| ------ | ----------------------- | ----------- |
| `POST` | `/api/heatmap`          | Upload one or more JFR files (`multipart/form-data` field name `files`). Returns `{uuid, stats}` once the heatmaps are generated. |
| `GET`  | `/api/heatmap/{uuid}`   | Download the HTML heatmap associated with the supplied UUID. CPU and allocation variants append `-cpu`/`-alloc` to the UUID. |
| `GET`  | `/api/flamegraph/{uuid}`| Download the HTML flamegraph (non-heatmap) generated for the supplied UUID. CPU and allocation variants append `-cpu`/`-alloc` alongside the `-html` suffix. |
| `GET`  | `/api/storage/stats`    | Inspect RocksDB statistics (estimated key count, live data size, etc.). |
| `GET`  | `/api/storage/keys`     | List all stored keys for housekeeping scripts. |

The stats payload mirrors the structure produced by [`jfr.service/jfr-stats`](src/clj/jfr/service.clj), making it straightforward to surface additional metadata.

---

## Project layout

```
├── resources/            # Static assets & config.edn
├── src/clj/jfr/          # Ring handlers, storage helpers, async-profiler integration
├── test/                 # Unit tests covering helpers and storage components
├── Dockerfile            # Production image with async-profiler binaries baked in
└── deps.edn              # Clojure CLI configuration and build aliases
```

- [`jfr.core`](src/clj/jfr/core.clj) wires the HTTP routes and launches the [`http-kit`](https://github.com/http-kit/http-kit) server.
- [`jfr.service`](src/clj/jfr/service.clj) performs merging, conversion, and statistics extraction using async-profiler's `JfrToHeatmap`.
- [`jfr.storage`](src/clj/jfr/storage.clj) persists results in RocksDB and exposes simple housekeeping helpers.

---

## Testing

```bash
clj -M:test
```

The suite exercises storage initialization, configuration loading, and helper utilities.

---

## Troubleshooting

- **RocksDB native library errors** – ensure the `librocksdbjni` library is available. Docker builds ship with the dependency; on bare metal install RocksDB or use the precompiled binaries bundled with modern JVMs.
- **Large uploads fail** – the server is configured to accept files up to 1 GiB. Raise `:max-body` in [`jfr.core/-main`](src/clj/jfr/core.clj) if you need larger recordings.
- **Heatmap generation slow** – `jfr-merger` runs conversions sequentially. If you process massive profiles consider scaling horizontally or offloading conversion to a worker queue.

---

## Contributing

Issues and pull requests are welcome! If you're planning large changes, open an issue first so we can align on direction.

1. Fork and clone the repository.
2. Create a feature branch.
3. Add tests or examples when feasible.
4. Submit a pull request describing your changes.

---
