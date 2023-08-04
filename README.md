#### Build and push image    

    $ docker build -t neusa .
    $ docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa:latest
    $ docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa