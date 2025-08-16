# jfr-merger

FIXME: description

## Installation

> cd ...
> mkdir -p lib && curl -L -o lib/jfr-converter.jar https://github.com/async-profiler/async-profiler/releases/download/v4.1/jfr-converter.jar
>
> clj -M:repl

## Usage

docker build -t jfr-merger .

docker run -p 8080:8080 jfr-merger
