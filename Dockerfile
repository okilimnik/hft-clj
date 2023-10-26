FROM ghcr.io/graalvm/graalvm-community:21
COPY . .
ENV GOOGLE_APPLICATION_CREDENTIALS=./gcp.json
CMD ["./lein", "run", "-d"]

# docker build -t neusa .
# docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft:latest
# docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft

# docker build -t neusa .
# docker tag neusa neuronsages/neusa-jobs:latest
# docker push neuronsages/neusa-jobs:latest

# docker run -it --rm --memory=1800m --cpus=1 --name neusa neusa

# export GOOGLE_APPLICATION_CREDENTIALS=/Users/okilimnik/Projects/hft-clj/gcp.json
# lein run -d
# java -XX:+UseZGC -Xmx4g -jar target/uberjar/app.jar -d