FROM fedora:42 AS builder

RUN dnf install -y java-21-openjdk curl rlwrap && \
    curl -L -O https://github.com/clojure/brew-install/releases/download/1.12.1.1561/linux-install.sh && \
    chmod +x linux-install.sh && \
    mkdir -p /app/clojure && \
    ./linux-install.sh -p /app/clojure && \
    rm linux-install.sh

WORKDIR /app
COPY . /app

RUN mkdir -p lib && curl -L -o lib/jfr-converter.jar https://github.com/async-profiler/async-profiler/releases/download/nightly/jfr-converter.jar

RUN  /app/clojure/bin/clj -T:build uber
RUN ls -la /app/target

FROM fedora:42

RUN dnf install -y java-21-openjdk
RUN mkdir -p /app/storage

EXPOSE 5000
WORKDIR /app
COPY --from=builder /app/target/app-0.1.0-standalone.jar /app/app-0.1.0-standalone.jar
COPY config.edn /app/config.edn

CMD ["java", "-jar", "app-0.1.0-standalone.jar"]