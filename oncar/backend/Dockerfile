# Imagem da aplicacao, construida em duas etapas para nao levar o Maven ao runtime.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn ./.mvn
COPY mvnw pom.xml ./
# checkouts feitos no Windows podem perder o bit de execucao ou trazer CRLF no wrapper
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src ./src
RUN ./mvnw -B -q -DskipTests package && cp target/oncar-*.jar app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
# a aplicacao nao precisa de root dentro do container
RUN groupadd --system oncar && useradd --system --gid oncar oncar
COPY --from=build /app/app.jar app.jar
USER oncar
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# a porta so abre depois que o Spring termina de subir (bash ja vem na imagem)
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
    CMD bash -c '</dev/tcp/127.0.0.1/8080' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
