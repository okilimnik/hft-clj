FROM clojure AS lein
WORKDIR /src
COPY . /src
RUN lein do clean, uberjar

FROM container-registry.oracle.com/graalvm/jdk:22
RUN microdnf install freetype fontconfig
COPY --from=lein /src/target/hft.jar ./
CMD ["java", "-jar", "hft.jar"]