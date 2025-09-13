# jfr-merger

**jfr-merger** is a web UI for generating heatmaps from Java Flight Recorder (JFR) files.

You can upload multiple JFR files—they will be merged and converted into a

## Installation

> cd ...
> mkdir -p lib && curl -L -o lib/jfr-converter.jar https://github.com/async-profiler/async-profiler/releases/download/v4.1/jfr-converter.jar
>
> clj -M:repl
>
> clj -M:test
> 
> clj -X:uberjar

## Usage and tips

>docker build -t jfr-merger .
>
>docker run -p 8080:8080 jfr-merger
>
>docker exec -it jfr-merger /bin/bash
>
>docker run --rm -it --entrypoint bash jfr-merger

or just

>docker compose up

## Checking the Docker build cache

To make sure layer caching works, run the build twice. The second run
should show many steps marked as cached and finish much faster.

```bash
docker buildx build --target builder \
  --cache-from=type=local,src=/tmp/.buildx-cache \
  --cache-to=type=local,dest=/tmp/.buildx-cache-new \
  --load -t jfr-merger-ci .

mv /tmp/.buildx-cache-new /tmp/.buildx-cache

docker buildx build --target builder \
  --cache-from=type=local,src=/tmp/.buildx-cache \
  --cache-to=type=local,dest=/tmp/.buildx-cache-new \
  --load -t jfr-merger-ci .
```

If the cache is used, the output of the second command will contain
`CACHED` for most layers.
