FROM ghcr.io/graalvm/graalvm-community:21

COPY target/hft.jar ./
COPY binance.config.edn ./
COPY gcp.json ./
COPY image-counter.txt ./
ENV GOOGLE_APPLICATION_CREDENTIALS=./gcp.json
CMD ["java", "-XX:+UseZGC", "-jar", "hft.jar", "-d"]

# docker build -f Dockerfile.ds -t neusa-ds .
# docker tag neusa-ds asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft:latest
# docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft

# docker run -it --rm --memory=8000m --cpus=1 --name neusa-trade neusa-trade

# GOOGLE_APPLICATION_CREDENTIALS=./gcp.json lein run -d