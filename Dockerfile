FROM ghcr.io/graalvm/jdk-community:latest
COPY target/uberjar/app.jar ./
COPY binance.config.edn ./
COPY gcp.json ./
CMD ["java", "-jar", "app.jar", "-d"]

# docker build -t neusa .
# docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft:latest
# docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft