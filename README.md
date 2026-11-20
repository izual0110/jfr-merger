# jfr-merger

Upload one or more Java Flight Recorder (`.jfr`) files to inspect them together in your browser. `jfr-merger` merges the recordings and uses async-profiler to generate a default heatmap plus CPU-only and allocation-only heatmaps. It can also generate flame graphs, run background pattern detection, and analyze heap dumps with JOL.

## Run locally

Use the [Clojure CLI](https://clojure.org/guides/install_clojure) and JDK 27 to match the Docker build. The application dependencies, including the JFR converter and JOL, are declared in [`deps.edn`](deps.edn). You do not need to download the converter manually.

```sh
git clone https://github.com/izual0110/jfr-merger.git
cd jfr-merger
clj -X:uberjar
```

This command starts the service; it does not build a JAR. Open [http://localhost:8080/index.html](http://localhost:8080/index.html) with the default configuration.

On macOS, you can install the Clojure CLI with `brew install clojure/tools/deps`. On Linux, if `clj` or `rlwrap` is missing, review [`prepare-env.sh`](prepare-env.sh) before running `./prepare-env.sh` from the repository root. It can install system packages. It does not install Java or async-profiler.

### Run with Docker

```sh
docker compose up --build
```

Compose publishes port `8080` and mounts the `app_storage` volume at `/app/storage` so data survives container replacement. To open a shell in the running Compose service, use `docker compose exec jfr /bin/sh`.

To build and run without Compose, use:

```sh
docker build -t jfr-merger .
docker run --rm --name jfr-merger-local -p 8080:8080 -v jfr-merger-storage:/app/storage jfr-merger
```

For debugging, run these commands as needed:

```sh
# Open a shell in the image without starting the application.
docker run --rm -it --entrypoint /bin/sh jfr-merger

# Open a shell in the standalone container while it is running.
docker exec -it jfr-merger-local /bin/sh
```

To reclaim disk space across Docker:

```sh
docker system prune -a
```

**This is not project-scoped:** it removes stopped containers, unused networks and images, and build cache. It does not remove volumes by default.

### Build an executable JAR

```sh
clj -T:build uber
java --enable-native-access=ALL-UNNAMED -jar target/jfr-merger-0.1.1.jar
```

### Configure storage

Edit [`resources/config.edn`](resources/config.edn) to change the storage paths or server settings. The default storage settings are:

```clojure
{:jfr-data-path "storage/jfrs"
 :temp-dir      "storage/temp"}
```

RocksDB stores results and history in `:jfr-data-path`. The service uses `:temp-dir` for working files. The service process needs write access to both paths. Use persistent storage if you need results to survive redeployment.

## Use the browser UI

1. Open the upload page and select one or more `.jfr` files.
2. Select any optional analyses, then click **Merge & Generate**.
3. Open a heatmap or review the event count, UTC start and end times, duration in milliseconds, and counts by event type.

You can reopen results from the history on the same instance. Use the heap dump page to generate JOL statistics for a heap dump.

### Generate a sample recording

Use the native [async-profiler 4.6 distribution](https://github.com/async-profiler/async-profiler/releases/tag/v4.6) to record CPU and allocation events from [`test/BadPatternsDemo.java`](test/BadPatternsDemo.java). Run the commands from the repository root, then upload `profile.jfr` in the browser.

**Linux (x64)** — on ARM64, replace `linux-x64` with `linux-arm64` in the URL:

```sh
mkdir -p lib/async-profiler
curl -fsSL https://github.com/async-profiler/async-profiler/releases/download/v4.6/async-profiler-4.6-linux-x64.tar.gz \
  | tar -xzf - --strip-components=1 -C lib/async-profiler
java -agentpath:./lib/async-profiler/lib/libasyncProfiler.so=start,event=cpu,alloc,file=profile.jfr test/BadPatternsDemo.java
```

**macOS** — the system `tar` can extract ZIP archives without `-z`:

```sh
mkdir -p lib/async-profiler
curl -fsSL https://github.com/async-profiler/async-profiler/releases/download/v4.6/async-profiler-4.6-macos.zip \
  | tar -xf - --strip-components=1 -C lib/async-profiler
java -agentpath:./lib/async-profiler/lib/libasyncProfiler.dylib=start,event=cpu,alloc,file=profile.jfr test/BadPatternsDemo.java
```

To convert a recording without starting the service, you can download and run the standalone converter:

```sh
mkdir -p lib
curl -fsSL -o lib/jfr-converter.jar https://github.com/async-profiler/async-profiler/releases/download/v4.6/jfr-converter.jar
java -jar lib/jfr-converter.jar --cpu profile.jfr cpu.html
```

## HTTP API

| Method | Path | Request and result |
| --- | --- | --- |
| `POST` | `/api/convertor` | Send one or more `files` parts as `multipart/form-data`. Optional fields: `addFlamegraph=true` and `addDetector=true`. Returns `201` with the UUID as the response body. |
| `GET` | `/api/convertor/{id}` | Get a generated HTML visualization. See the ID suffixes below. |
| `GET` | `/api/detector/{uuid}` | Get the HTML detector report for a recording. |
| `POST` | `/api/heapdump` | Send the heap dump in the multipart `file` field. Returns JOL statistics as plain text. |
| `POST` | `/api/filesystem/process` | Send JSON with `path` on the **server** filesystem. Optionally set `type` to `jfr` or `heapdump`. For JFR, the response includes the UUID and heatmap links; for a heap dump, it includes the statistics. |
| `GET` | `/api/history` | List recordings and their statistics. |
| `GET` | `/api/history-heapdump-stats` | List heap dump analysis results. |
| `POST` | `/api/history/{uuid}/name` | Rename a recording with a JSON body containing `name`. |
| `GET` | `/api/storage/stats` | Get RocksDB statistics. |
| `GET` | `/api/storage/keys` | List RocksDB keys. |
| `POST` | `/api/clear` | Delete all RocksDB keys, including results and history. |

Use the bare UUID for the default heatmap. Append `-cpu` or `-alloc` for the filtered heatmaps, or `-flame`, `-flame-cpu`, or `-flame-alloc` for flame graphs if generated.

**Deployment:** The app does not authenticate these routes. Restrict access to trusted users: the API can process files on the server and delete stored results. Treat recordings and heap dumps as sensitive data.

## Develop and test

```sh
clj -M:test
```

### Update dependencies

```sh
# Check for updates without changing dependencies.
clj -M:outdated

# Apply updates without prompting.
clj -M:outdated --upgrade --force
```

Review the changes to `deps.edn` and rerun the tests after an upgrade.

### Start a REPL

`clj -M:repl` starts nREPL, not the HTTP server. The alias listens on `0.0.0.0:5000` and configures an 8 GiB JVM heap; use it only in a trusted environment. To start and stop HTTP from the REPL:

```clojure
(require 'jfr.core)
(jfr.core/start-server)
;; When finished:
(jfr.core/stop-server)
```

The application code is in [`src/clj/jfr/`](src/clj/jfr/), the browser assets are in [`resources/public/`](resources/public/), and tests are in [`test/`](test/). See [AGENTS.md](AGENTS.md) for code conventions and verification steps.
