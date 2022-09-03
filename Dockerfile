# Container to build app using gradle
FROM canto-base AS BUILD_IMAGE
ENV APP_HOME=/usr/app/
WORKDIR $APP_HOME
COPY . .
RUN gradle --no-daemon --stacktrace shadowJar

# Actual container
FROM openjdk:11
ENV APP_HOME=/usr/app
ENV ARTIFACT_NAME=lib-deploy.jar
COPY --from=BUILD_IMAGE $APP_HOME/lib/build/libs/$ARTIFACT_NAME app.jar
