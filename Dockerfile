# syntax=docker/dockerfile:1.4

FROM fedora:42 AS base
RUN dnf install -y java-25-openjdk curl

FROM base AS builder
RUN dnf install -y rlwrap && \
    curl -L -O https://github.com/clojure/brew-install/releases/download/1.12.3.1577/linux-install.sh && \
    chmod +x linux-install.sh && \
    mkdir -p /app/clojure && \
    ./linux-install.sh -p /app/clojure && \
    rm linux-install.sh
WORKDIR /app
COPY . /app
RUN mkdir -p lib && curl -L -o lib/jfr-converter.jar https://github.com/async-profiler/async-profiler/releases/download/v4.1/jfr-converter.jar
RUN --mount=type=cache,id=clojure-m2,target=/root/.m2 \
    --mount=type=cache,id=clojure-gitlibs,target=/root/.gitlibs \
    bash -lc 'set -euo pipefail; \
      /app/clojure/bin/clojure -T:build uber; \
      rm -rf /app/cache/m2 /app/cache/gitlibs; \
      mkdir -p /app/cache/m2 /app/cache/gitlibs; \
      cp -a /root/.m2/. /app/cache/m2/; \
      cp -a /root/.gitlibs/. /app/cache/gitlibs/'

FROM base
EXPOSE 8080
WORKDIR /app
COPY --from=builder /app/target/app-0.1.0-standalone.jar /app/app-0.1.0-standalone.jar

CMD ["sh", "-c", "java --enable-native-access=ALL-UNNAMED $JAVA_OPTS -XX:+PrintFlagsFinal -jar app-0.1.0-standalone.jar"]
