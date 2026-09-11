# Etapa 1 - Compilação 
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app 

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn package -DskipTests

# Etapa 2 - Execução
FROM eclipse-temurin:21-alpine

COPY --from=builder app/target/echoes-server.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
