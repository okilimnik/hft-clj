FROM nvidia/cuda:11.7.1-cudnn8-runtime-ubuntu20.04

ENV JAVA_HOME=/opt/graalvm-community-java21
COPY --from=ghcr.io/graalvm/graalvm-community:21 $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY target/hft.jar ./
COPY gcp.json ./
ENV GOOGLE_APPLICATION_CREDENTIALS=./gcp.json
CMD ["java", "-jar", "app.jar", "-r"]

# docker build -t neusa .
# docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft:latest
# docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft

# GOOGLE_APPLICATION_CREDENTIALS=/Users/okilimnik/Projects/hft-clj/gcp.json java -jar target/hft.jar -r