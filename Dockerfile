FROM fedora:42 AS base
RUN dnf install -y java-24-openjdk curl

FROM base AS builder
RUN dnf install -y rlwrap && \
    curl -L -O https://github.com/clojure/brew-install/releases/download/1.12.2.1565/linux-install.sh && \
    chmod +x linux-install.sh && \
    mkdir -p /app/clojure && \
    ./linux-install.sh -p /app/clojure && \
    rm linux-install.sh
WORKDIR /app
COPY . /app
RUN mkdir -p lib && curl -L -o lib/jfr-converter.jar https://github.com/async-profiler/async-profiler/releases/download/v4.1/jfr-converter.jar
RUN  /app/clojure/bin/clj -T:build uber

FROM base
EXPOSE 5000
WORKDIR /app
COPY --from=builder /app/target/app-0.1.0-standalone.jar /app/app-0.1.0-standalone.jar

CMD ["sh", "-c", "java $JAVA_OPTS -XX:+PrintFlagsFinal -jar app-0.1.0-standalone.jar"]