FROM registry.access.redhat.com/ubi8/openjdk-17:1.11 AS builder
LABEL maintainer="IBM Java Engineering at IBM Cloud"

WORKDIR /app
COPY . /app

RUN mvn -N wrapper:wrapper -Dmaven=3.8.4
RUN ./mvnw install

FROM registry.access.redhat.com/ubi8/openjdk-17:1.11
LABEL maintainer="IBM Java Engineering at IBM Cloud"

COPY --from=builder /app/target/knative-eventing-1.0-SNAPSHOT.jar /app.jar

ENV JAVA_OPTS=""
ENV PORT=8080

EXPOSE 8080

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar --add-opens java.base/java.time=ALL-UNNAMED /app.jar" ]
