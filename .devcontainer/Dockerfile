FROM quay.io/fedora/fedora-minimal:44

ARG CLOJURE_VERSION=1.12.6.1673

RUN dnf install -y \
      curl \
      docker-cli \
      docker-compose \
      findutils \
      git \
      gzip \
      java-26-openjdk-devel \
      rlwrap \
      tar \
    && curl -fsSL -o /tmp/linux-install.sh \
      "https://github.com/clojure/brew-install/releases/download/${CLOJURE_VERSION}/linux-install.sh" \
    && chmod +x /tmp/linux-install.sh \
    && /tmp/linux-install.sh \
    && rm -f /tmp/linux-install.sh \
    && dnf clean all \
    && rm -rf /var/cache/dnf

WORKDIR /workspace/jfr-merger

CMD ["sleep", "infinity"]
