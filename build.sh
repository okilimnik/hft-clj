#!/bin/sh

clj -M -e "(compile 'hft.core)"  
clj -M:uberjar --main-class hft.core

docker build -t neusa-trade .
docker tag neusa-trade asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft:latest
docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft