#FROM openjdk:21
FROM 756244784198.dkr.ecr.us-east-1.amazonaws.com/golden-image-repo:latest
ARG JAR_FILE=target/*.jar
ARG AWS_SECRETS_PATH
ENV aws.secret.path=${AWS_SECRETS_PATH}
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","app.jar"]