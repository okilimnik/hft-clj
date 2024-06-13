FROM ghcr.io/graalvm/graalvm-community:21

COPY target/hft.jar ./
COPY binance.config.edn ./
COPY gcp.json ./
ENV GOOGLE_APPLICATION_CREDENTIALS=./gcp.json
CMD ["java", "-XX:+UseZGC", "-jar", "app.jar"]

# docker build -t neusa-trade .
# docker tag neusa-trade asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft:latest
# docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft