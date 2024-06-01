#### Build and push image    

    $ docker build -t neusa .
    $ docker tag neusa asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa:latest
    $ docker push asia-northeast1-docker.pkg.dev/neusa-a919b/neusa/neusa


#### Download data

    $ gsutil -m cp -R gs://neusa-datasets/order_book20 dataset/

Best: 75 train 74 valid


ffmpeg -framerate 5 -pattern_type glob -i 'resources/*.png' \
  -c:v libx264 -pix_fmt yuv420p out.mp4