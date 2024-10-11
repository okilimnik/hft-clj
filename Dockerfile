FROM clojure AS lein
WORKDIR /src
COPY . /src
RUN lein do clean, uberjar

FROM container-registry.oracle.com/graalvm/jdk:22
RUN yum update && yum install libfreetype6
COPY --from=lein /src/target/hft.jar ./
CMD ["java", "-jar", "hft.jar"]