FROM clojure:temurin-20-lein-alpine
COPY . .
RUN lein deps
CMD ["lein", "run", "-m","hft.dataset"]