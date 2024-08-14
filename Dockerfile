FROM clojure AS lein
WORKDIR /src
COPY . /src
RUN lein do clean, uberjar

FROM container-registry.oracle.com/graalvm/jdk:22
WORKDIR /src
COPY --from=lein /src/target/hft.jar ./
CMD ["java", "-jar", "/src/hft.jar", "-d"] 