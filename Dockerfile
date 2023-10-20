# FROM ghcr.io/graalvm/graalvm-community:latest
FROM amazoncorretto:21-alpine
COPY target/uberjar/app.jar ./
COPY binance.config.edn ./
COPY gcp.json ./
CMD ["java", "-jar", "app.jar", "-d"]

# docker build -t neusa .
# docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft:latest
# docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft

# docker build -t neusa .
# docker tag neusa neuronsages/neusa-jobs:latest
# docker push neuronsages/neusa-jobs:latest

# export GOOGLE_CLOUD_CREDENTIALS=/Users/okilimnik/Projects/hft-clj/gcp.json && lein run -d