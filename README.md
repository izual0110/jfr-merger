# jfr-merger

**jfr-merger** is a web UI for generating heatmaps from Java Flight Recorder (JFR) files.

You can upload multiple JFR files—they will be merged and converted into a

## Installation

> cd ...
> mkdir -p lib && curl -L -o lib/jfr-converter.jar https://github.com/async-profiler/async-profiler/releases/download/v4.1/jfr-converter.jar
>
> clj -M:repl

## Usage

>docker build -t jfr-merger .
>docker run -p 8080:8080 jfr-merger

or just

>docker compose up
