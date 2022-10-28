FROM registry.access.redhat.com/ubi8/openjdk-17:1.14 AS builder
LABEL maintainer="IBM Java Engineering at IBM Cloud"

WORKDIR /app
COPY . /app

RUN mvn -N wrapper:wrapper -Dmaven=3.8.4
RUN ./mvnw install

FROM registry.access.redhat.com/ubi8/openjdk-17:1.14
LABEL maintainer="IBM Java Engineering at IBM Cloud"

# disable vulnerable TLS algorithms
USER root
RUN sed -i 's/jdk.tls.disabledAlgorithms=/jdk.tls.disabledAlgorithms=SSLv2Hello, DES40_CBC, RC4_40, SSLv2, TLSv1, TLSv1.1, /g' /etc/java/java-17-openjdk/java-17-openjdk-17.0.3.0.7-2.el8_6.x86_64/conf/security/java.security
USER 1001

COPY --from=builder /app/target/knative-eventing-1.0-SNAPSHOT.jar /app.jar

ENV JAVA_OPTS=""
ENV PORT=8080

EXPOSE 8080

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar --add-opens java.base/java.time=ALL-UNNAMED /app.jar" ]
