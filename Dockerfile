FROM quay.io/fedora/fedora-minimal:43 AS base
RUN dnf install -y java-25-openjdk curl tar && dnf clean all && rm -rf /var/cache/yum

FROM base AS clojure
RUN dnf install -y rlwrap && \
    curl -L -O https://github.com/clojure/brew-install/releases/download/1.12.3.1577/linux-install.sh && \
    chmod +x linux-install.sh && \
    mkdir -p /app/clojure && \
    ./linux-install.sh -p /app/clojure && \
    rm linux-install.sh && \
    dnf clean all && \
    rm -rf /var/cache/yum
    
WORKDIR /app
COPY . /app
RUN mkdir -p lib && curl -L -o lib/jfr-converter.jar https://github.com/async-profiler/async-profiler/releases/download/v4.2/jfr-converter.jar

FROM clojure AS builder
WORKDIR /app
COPY . /app
RUN  /app/clojure/bin/clojure -T:build uber

FROM base
EXPOSE 8080
WORKDIR /app
COPY --from=builder /app/target/app-0.1.0-standalone.jar /app/app-0.1.0-standalone.jar

CMD ["sh", "-c", "java --enable-native-access=ALL-UNNAMED $JAVA_OPTS -XX:+PrintFlagsFinal -jar app-0.1.0-standalone.jar"]
