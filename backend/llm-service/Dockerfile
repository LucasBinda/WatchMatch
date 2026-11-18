# Build com o Maven Wrapper do próprio serviço; a imagem final só leva o JRE e o .jar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Dependências primeiro: mudar só o código não baixa tudo de novo
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
# Os testes rodam fora do Docker (./mvnw test)
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
