FROM ubuntu:20.04
RUN mkdir -p /ci/workdir /sdk
ENV PATH="/sdk/cmdline-tools/bin:/sdk/platform-tools:${PATH}"
ENV DEBIAN_FRONTEND=noninteractive
WORKDIR "/ci/workdir"
RUN apt update -y --fix-missing && apt install -y python2 python3 python3-pip git-core gnupg

RUN apt install -y --fix-missing wget unzip openjdk-11-jdk tzdata zip jq

RUN cd /sdk && wget https://dl.google.com/android/repository/commandlinetools-linux-8512546_latest.zip \
    && unzip commandlinetools-linux-8512546_latest.zip

ENV ANDROID_HOME=/sdk
ENV ANDROID_NDK_HOME=/sdk/ndk/21.4.7075529
RUN sdkmanager --update --sdk_root=${ANDROID_HOME}
RUN yes | sdkmanager --sdk_root=${ANDROID_HOME} --licenses
RUN sdkmanager --sdk_root=${ANDROID_HOME} "tools" "platform-tools" "platforms;android-32" "build-tools;32.0.0" "cmake;3.18.1" "ndk;21.4.7075529"
RUN cd / && wget -q https://dl.google.com/dl/cloudsdk/channels/rapid/google-cloud-sdk.tar.gz && \
    tar xf google-cloud-sdk.tar.gz && \
    rm google-cloud-sdk.tar.gz && \
    ./google-cloud-sdk/install.sh
ENV PATH "/google-cloud-sdk/bin:${PATH}"

ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8
RUN apt update -y --fix-missing && apt install -y --fix-missing curl psmisc nodejs npm
RUN npm install -g firebase-tools
RUN apt-get install -y tidy