FROM debian:11

SHELL ["/bin/bash", "-c"]

ARG PHP_TOOLS_PATH=/PHP/tools
ARG PHP_PROJECTS_PATH=/PHP/projects
ARG jdk_url_part=linux-aarch64
ARG jdk_path=/data/App/jdk-15.0.1

#https://download.java.net/java/GA/jdk15/779bf45e88a44cbd9ea6621d33e33db1/36/GPL/openjdk-15_linux-x64_bin.tar.gz
#https://download.java.net/java/GA/jdk15/779bf45e88a44cbd9ea6621d33e33db1/36/GPL/openjdk-15_linux-aarch64_bin.tar.gz

#https://download.java.net/java/GA/jdk15.0.1/51f4f36ad4ef43e39d0dfdbaf6549e32/9/GPL/openjdk-15.0.1_linux-x64_bin.tar.gz

#ARG jdk_path=/data/App/jdk-11.0.1
#https://download.java.net/java/GA/jdk11/13/GPL/openjdk-11.0.1_linux-x64_bin.tar.gz

RUN apt-get update \
    && apt -y install php php-common curl php-cli php7.4-sqlite3 \
    php-mbstring git unzip php7.4-curl php7.4-xml vim python3 \
    php-zip php-mysql php-intl php7.4-gd php7.4-bcmath

RUN curl -sS https://getcomposer.org/installer -o composer-setup.php
RUN php composer-setup.php --install-dir=/usr/local/bin --filename=composer

ADD ./PHP/tools /PHP/tools

WORKDIR ${PHP_TOOLS_PATH}
run composer install

# Psalm setup
WORKDIR /root/.composer
RUN composer global require --dev "vimeo/psalm:5.7.0"
WORKDIR /root/.composer/vendor/vimeo/psalm

WORKDIR ${PHP_PROJECTS_PATH}

RUN apt-get update \
    && apt-get install -y zlib1g zlib1g-dev curl \
        libextutils-cbuilder-perl libextutils-makemaker-cpanfile-perl \
        gcc expect make wget gettext zip unzip git

# Joern setup
RUN mkdir -p /data/App \
    && cd /data/App \
    && wget https://github.com/sbt/sbt/releases/download/v1.6.1/sbt-1.6.1.zip \
    && unzip *.zip \
    && rm *.zip \
    && mv sbt/ sbt-1.6.1/ \
    && wget https://download.java.net/java/GA/jdk15.0.1/51f4f36ad4ef43e39d0dfdbaf6549e32/9/GPL/openjdk-15.0.1_${jdk_url_part}_bin.tar.gz \
    && tar zxvf *.tar.gz \
    && rm *.tar.gz

ENV LANG=en_US.UTF-8 \
    JAVA_HOME=$jdk_path \
    PATH=/data/App/sbt-1.6.1/bin:$jdk_path/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

WORKDIR ${PHP_TOOLS_PATH}
# Clone Joern from the official repository
RUN git clone https://github.com/joernio/joern.git

WORKDIR ${PHP_TOOLS_PATH}/joern
# Checkout to a stable commit
RUN git checkout 66089b8680151460e73fdf6fc9e2f96907ab6029
# Supplement the function signatures that Joern knows about with relevant ones
# from the evaluation dataset that are recoverable from docblocks. To be
# removed once Joern can parse PHP docblocks.
RUN cat ../known_function_signatures.txt >> ./joern-cli/frontends/php2cpg/src/main/resources/known_function_signatures.txt
# Remove .git directories since we are not copying the parent repository .git
# directories and files. When sbt builds Joern, it will see the broken back
# reference in the submodule file.
RUN rm -rf ./.git*
RUN sbt stage

CMD ["/bin/bash"]
