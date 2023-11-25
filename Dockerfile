FROM nvidia/cuda:11.7.1-cudnn8-runtime-ubuntu20.04

ENV JAVA_HOME=/opt/graalvm-community-java21
COPY --from=ghcr.io/graalvm/graalvm-community:21 $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY target/uberjar/app.jar ./
COPY binance.config.edn ./
COPY gcp.json ./
COPY image-counter.txt ./
COPY dataset ./dataset
ENV GOOGLE_APPLICATION_CREDENTIALS=./gcp.json
CMD ["java", "-jar", "app.jar", "-n"]

# docker build -t neusa .
# docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft:latest
# docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft

# docker build -t neusa .
# docker tag neusa neuronsages/neusa-jobs:latest
# docker push neuronsages/neusa-jobs:latest

# docker run -it --rm --memory=8000m --cpus=1 --name neusa neusa

# GOOGLE_APPLICATION_CREDENTIALS=/Users/okilimnik/Projects/hft-clj/gcp.json lein run -n