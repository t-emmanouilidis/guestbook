FROM openjdk:8-alpine

COPY target/uberjar/guestbook.jar /guestbook/app.jar

COPY dev-config.edn /guestbook/config.edn

EXPOSE 3000

CMD ["java", "-jar", "-Dconf=/guestbook/config.edn", "/guestbook/app.jar"]
