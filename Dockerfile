# Etapa 1: build com Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Limita a memória do Maven: o build do plano gratuito do Render tem pouca RAM
ENV MAVEN_OPTS="-Xmx384m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Os testes rodam no CI (GitHub Actions); aqui só compila o código da aplicação
RUN mvn -B package -Dmaven.test.skip=true

# Etapa 2: imagem final só com o JRE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/encurtador-url-*.jar app.jar
USER app
EXPOSE 8080
# Usa até 75% da memória do contêiner (o plano gratuito do Render tem 512 MB)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
