FROM ghcr.io/graalvm/graalvm-community:21
COPY target/uberjar/app.jar ./
COPY binance.config.edn ./
COPY gcp.json ./
ENV GOOGLE_APPLICATION_CREDENTIALS=./gcp.json
CMD ["java", "-XX:+UseZGC", "-jar", "app.jar", "-d"]

# docker build -t neusa .
# docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft:latest
# docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft

# docker build -t neusa .
# docker tag neusa neuronsages/neusa-jobs:latest
# docker push neuronsages/neusa-jobs:latest

# docker run -it --rm --memory=8000m --cpus=1 --name neusa neusa

# GOOGLE_APPLICATION_CREDENTIALS=/Users/okilimnik/Projects/hft-clj/gcp.json lein run -d