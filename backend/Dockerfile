FROM eclipse-temurin:21-jdk AS build

WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app/target/locus-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /app/models/all-MiniLM-L6-v2.onnx /app/models/all-MiniLM-L6-v2.onnx
COPY --from=build /app/models/tokenizer.json /app/models/tokenizer.json

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /app/models/ner

RUN curl -fL -o /app/models/ner/model.onnx \
    "https://huggingface.co/runt1merr0r07/LocusNER/resolve/main/model.onnx"

RUN curl -fL -o /app/models/ner/tokenizer.json \
    "https://huggingface.co/runt1merr0r07/LocusNER/resolve/main/tokenizer.json"

RUN curl -fL -o /app/models/ner/tokenizer_config.json \
    "https://huggingface.co/runt1merr0r07/LocusNER/resolve/main/tokenizer_config.json"

RUN curl -fL -o /app/models/ner/config.json \
    "https://huggingface.co/runt1merr0r07/LocusNER/resolve/main/config.json"

RUN curl -fL -o /app/models/ner/labels.json \
    "https://huggingface.co/runt1merr0r07/LocusNER/resolve/main/labels.json"

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]