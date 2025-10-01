# jfr-merger

**jfr-merger** is a web UI for generating heatmaps from Java Flight Recorder (JFR) files.

You can upload multiple JFR files—they will be merged and converted into a

## Installation

> cd ...
> mkdir -p lib && curl -L -o lib/jfr-converter.jar https://github.com/async-profiler/async-profiler/releases/download/v4.1/jfr-converter.jar
>
> mkdir -p lib && curl -fsSL "https://github.com/async-profiler/async-profiler/releases/download/v4.1/async-profiler-4.1-linux-x64.tar.gz" | tar -xz -C lib
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


generate profile for bad patterns
> java -agentpath:$(pwd)/lib/async-profiler-4.1-linux-x64/lib/libasyncProfiler.so=start,event=cpu,file=profile.html  test/BadPatternsDemo.java
