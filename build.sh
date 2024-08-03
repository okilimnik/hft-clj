#!/bin/sh

rm -rf classes
mkdir classes
clj -M -e "(compile 'hft.core)"  
clj -M:uberjar --main-class hft.core

#docker build -t neusa .
#docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft:latest
#docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa-hft