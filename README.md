#### Build and push image    

    $ docker build -t neusa .
    $ docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa:latest
    $ docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa


#### Download data

    $ gsutil -m cp -R gs://neusa-datasets/order_book20 dataset/

Best: 75 train 74 valid