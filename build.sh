#!/bin/sh

lein do clean, uberjar

docker build -f Dockerfile.native -t neusa .
docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft:latest
#docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft