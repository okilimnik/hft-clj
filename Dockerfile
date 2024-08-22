FROM clojure AS lein
WORKDIR /src
COPY . /src
RUN lein do clean, uberjar

FROM container-registry.oracle.com/graalvm/jdk:22
COPY --from=lein /src/target/hft.jar ./
COPY --from=lein /src/binance.config.edn ./
CMD ["java", "-jar", "hft.jar"] 