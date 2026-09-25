# Linux environment to build and run the showcase (JVM and native modes) with a virtual X server.
#
#   docker build -t quarkus-desktop-showcase-linux docker/linux
#   docker run --rm --init -v "$PWD":/showcase -v "$HOME/.m2":/root/.m2 quarkus-desktop-showcase-linux java tools/Cycle.java linux
#
# The GraalVM of the Quarkus builder image is GraalVM CE for JDK 25 (an Innovation release line, not the Oracle GraalVM
# 25.0.x of a Windows machine) : compare Linux runs with Linux runs only.
FROM quay.io/quarkus/ubi9-quarkus-graalvmce-builder-image:jdk-25 AS graalvm

FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive LANG=C.UTF-8
RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb xauth \
        libx11-6 libxext6 libxrender1 libxi6 libxtst6 libxrandr2 libxinerama1 libxcursor1 libxxf86vm1 \
        libfontconfig1 libfreetype6 libcups2t64 libasound2t64 libgtk-3-0t64 libgl1 libgl1-mesa-dri libegl1 \
        fonts-dejavu-core fonts-liberation fonts-noto-core fonts-noto-cjk fonts-noto-color-emoji \
        build-essential zlib1g-dev libfreetype-dev \
        curl unzip ca-certificates git \
    && rm -rf /var/lib/apt/lists/*
# No sound card in a container : the ALSA null device (javax.sound pages never play audibly anyway)
RUN printf 'pcm.!default {\n    type null\n}\n' > /etc/asound.conf
COPY --from=graalvm /opt/graalvm /opt/graalvm
# GTK look and feel pages : a fixed theme
ENV JAVA_HOME=/opt/graalvm GRAALVM_HOME=/opt/graalvm PATH=/opt/graalvm/bin:$PATH GTK_THEME=Adwaita
COPY xvfb-exec /usr/local/bin/xvfb-exec
RUN chmod +x /usr/local/bin/xvfb-exec
WORKDIR /showcase
ENTRYPOINT ["xvfb-exec"]
