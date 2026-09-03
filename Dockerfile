# Etapa 1: build da aplicação
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copia só o necessário primeiro (cache inteligente)
COPY pom.xml .
RUN mvn dependency:go-offline

# Agora copia o resto
COPY src ./src

# Builda o jar
RUN mvn clean package -DskipTests

# Etapa 2: imagem final leve
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# Copia o jar gerado da etapa anterior
COPY --from=builder /app/target/*.jar app.jar

ENV SERVER_PORT=443

# Expõe a porta padrão
EXPOSE ${SERVER_PORT}

# Comando de execução
ENTRYPOINT ["java", "-jar", "app.jar"]
