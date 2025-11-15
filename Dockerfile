FROM quay.io/fedora/fedora-minimal:43 AS base
RUN dnf install -y java-25-openjdk-headless tar gzip && dnf clean all && rm -rf /var/cache/yum

FROM base AS clojure
RUN curl -L -O https://github.com/clojure/brew-install/releases/download/1.12.3.1577/linux-install.sh && \
    chmod +x linux-install.sh && \
    mkdir -p /app/clojure && \
    ./linux-install.sh -p /app/clojure && \
    rm linux-install.sh && \
    mkdir -p /app/lib && \
    curl -L -o /app/lib/jfr-converter.jar https://github.com/async-profiler/async-profiler/releases/download/v4.2/jfr-converter.jar
    
WORKDIR /app
COPY . /app
RUN  /app/clojure/bin/clojure -T:build uber

FROM base
EXPOSE 8080
WORKDIR /app
COPY --from=clojure /app/target/jfr-merger-0.1.1.jar /app/jfr-merger-0.1.1.jar

CMD ["sh", "-c", "java --enable-native-access=ALL-UNNAMED $JAVA_OPTS -XX:+PrintFlagsFinal -jar jfr-merger-0.1.1.jar"]
