FROM quay.io/fedora/fedora-minimal:43 AS base
RUN dnf install -y java-26-openjdk-headless tar gzip && dnf clean all && rm -rf /var/cache/yum

#only for jfr-merger-ci
FROM base AS clojure
RUN curl -L -O https://github.com/clojure/brew-install/releases/download/1.12.4.1582/linux-install.sh && \
    chmod +x linux-install.sh && \
    mkdir -p /app/clojure && \
    ./linux-install.sh -p /app/clojure && \
    rm linux-install.sh

WORKDIR /app

FROM clojure AS builder
COPY ./src /app/src
COPY ./deps.edn /app/deps.edn
COPY ./resources /app/resources
RUN /app/clojure/bin/clojure -T:build uber

FROM base AS aot-cache
WORKDIR /app
COPY --from=builder /app/target/jfr-merger-0.1.1.jar /app/jfr-merger-0.1.1.jar

RUN java --enable-native-access=ALL-UNNAMED \
    -XX:AOTCacheOutput=/app/jfr-merger-0.1.1.aot \
    -cp /app/jfr-merger-0.1.1.jar \
    clojure.main \
    -e "(require 'jfr.core) (jfr.core/-main) (jfr.core/stop-server)"

FROM base
EXPOSE 8080
WORKDIR /app
COPY --from=builder /app/target/jfr-merger-0.1.1.jar /app/jfr-merger-0.1.1.jar
COPY --from=aot-cache /app/jfr-merger-0.1.1.aot /app/jfr-merger-0.1.1.aot
CMD ["java", "--enable-native-access=ALL-UNNAMED", "-XX:AOTCache=/app/jfr-merger-0.1.1.aot", "-XX:+PrintFlagsFinal", "-jar", "jfr-merger-0.1.1.jar"]