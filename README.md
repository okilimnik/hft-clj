## Build native image (doesn't work)

~/graalvm-jdk-21.0.1+12.1/Contents/Home/lib/svm/bin/native-image -jar target/hft.jar --features=clj_easy.graal_build_time.InitClojureClasses --no-fallback --no-server target/hft
