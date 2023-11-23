#### Build and push image    

    $ docker build -t neusa .
    $ docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa:latest
    $ docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa


    TODO:
    1. Resnet 50
    2. Oversample target category
    3. Progressive test that updates thresholds to find out the best values that give less false positives over false negatives