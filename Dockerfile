FROM ubuntu:22.04 as quack

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
    && ./joern-install.sh --version=v2.0.290

# Copy Quack contents
COPY . /quack/
WORKDIR /quack

# Install Quack Python dependencies
RUN python3 -m pip install -r requirements.txt
#RUN python3 -m pip install -r pytests/requirements.txt

# Stage for installing testing dependencies
FROM quack as test-base
RUN apt-get update \
    && apt-get install -y git wget subversion

# Stage for testing on Piwik 0.4.5
FROM test-base as piwik-test
RUN git clone https://github.com/matomo-org/matomo.git /root/piwik
RUN git -C /root/piwik checkout tags/0.4.5

# Stage for testing on Joomla 3.0.2
FROM test-base as joomla-test
RUN git clone https://github.com/joomla/joomla-cms.git /root/joomla
RUN git -C /root/joomla checkout tags/3.0.2

# Stage for testing on CubeCart 5.2.0
FROM test-base as cubecart-test
RUN wget https://www.cubecart.com/download/CubeCart-5.0.0.zip -O CubeCart.zip
RUN mkdir -p /root/cubecart && unzip CubeCart.zip -d /root/cubecart

# Stage for testing on OpenWebAnalytics 1.5.6
FROM test-base as openwebanalytics-test
RUN git clone https://github.com/Open-Web-Analytics/Open-Web-Analytics.git /root/openwebanalytics
RUN git -C /root/openwebanalytics checkout tags/1.5.6

# Stage for testing Contao 3.2.4
FROM test-base as contao-test
RUN git clone https://github.com/contao/core.git /root/contao
RUN git -C /root/contao checkout tags/3.2.4

# Stage for testing on ForkCMS 5.8.3
FROM test-base as forkcms-test
RUN git clone https://github.com/forkcms/forkcms.git /root/forkcms
RUN git -C /root/forkcms checkout tags/5.8.3

# Stage for testing on WP hotel-bookings 1.10.2
FROM test-base as hotel-bookings-test
RUN git clone https://github.com/ThimPressWP/wp-hotel-booking.git /root/hotel-bookings
RUN git -C /root/hotel-bookings checkout 6a146dd21c516c36820d3f1684c5c74a2eb9b047

# Stage for testing on OpenCATS 0.9.5 (3)
FROM test-base as opencats-test
RUN git clone https://github.com/opencats/OpenCATS.git /root/opencats
RUN git -C /root/opencats checkout tags/0.9.5-3

# Stage for testing on WP-AIOSEO 4.1.0.1
FROM test-base as aioseo-test
RUN git clone https://github.com/awesomemotive/all-in-one-seo-pack.git /root/aioseo
RUN git -C /root/aioseo checkout tags/4.1.0.1

# Stage for testing on WP booking calendar 9.1.1
FROM test-base as booking-calendar-test
RUN svn checkout https://plugins.svn.wordpress.org/booking/tags/9.1.1/ /root/booking-calendar

# Stage for testing on WP lead-generated 1.2.3
FROM test-base as lead-generated-test
RUN svn checkout https://plugins.svn.wordpress.org/lead-generated/tags/1.23/ /root/lead-generated
