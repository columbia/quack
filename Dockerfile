FROM ubuntu:22.04

# Configure timezone so that tzdata dependency does not require user input to
# configure.
# Reference: https://grigorkh.medium.com/fix-tzdata-hangs-docker-image-build-cdb52cc3360d
ENV TZ=America/New_York
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Install Joern and Quack dependencies
RUN apt-get update \
    && apt-get install -y openjdk-19-jdk openjdk-19-jre curl unzip sudo \
        python3 python3-pip php

# Install Joern
WORKDIR /joern
RUN curl -L "https://github.com/joernio/joern/releases/latest/download/joern-install.sh" -o joern-install.sh \
    && chmod u+x joern-install.sh \
    && ./joern-install.sh --version=v2.0.156

# Copy Quack contents
COPY . /quack/
WORKDIR /quack

# Install Quack Python dependencies
RUN python3 -m pip install -r requirements.txt
#RUN python3 -m pip install -r pytests/requirements.txt
