#### Build and push image    

    $ docker build -t neusa .
    $ docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa:latest
    $ docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa


    TODO:
    1. Resnet 50
    2. Oversample target category
    3. Progressive test that updates thresholds to find out the best values that give less false positives over false negatives

    #### Known problems:
    1. A lot of false positives due to a large amount of noise, so profit moves to zero.
    (the amount of false positives is about 10%, but as the raw "wait" category is much much larger than the "buy", that has disastrous impact).
    Solutions: 
        Improve accuracy:
        1. More powerful network
        2. Oversample target category

        Improve threshold:
        1. Progressive test that updates thresholds to find out the best values that give less false positives over false negatives

        Market volatility plays for us:
        1. False positive still can bring profit if wait more than prediction head interval

        Find another crypto pair that is more volatile, and thus has more true positives, thus false negatives quantity doesn't make such impact.