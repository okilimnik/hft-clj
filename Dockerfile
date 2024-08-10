FROM container-registry.oracle.com/graalvm/jdk:22

COPY target/hft.jar ./
COPY binance.config.edn ./
CMD ["java", "-jar", "hft.jar", "-d"]