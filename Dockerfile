FROM ghcr.io/graalvm/graalvm-community:21

COPY target/hft.jar ./
COPY binance.config.edn ./
COPY gcp.json ./
ENV GOOGLE_APPLICATION_CREDENTIALS=./gcp.json
CMD ["java", "-jar", "hft.jar", "-d"]