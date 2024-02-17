#!/bin/bash
# Installs the composer dependencies for a project if the project uses
# composer, else initializes composer for the project

project_path=$1
project_name=$(basename ${project_path})
project_name_spec=$(echo "${project_name}" | tr '[:upper:]' '[:lower:]' | tr -dc '[a-z0-9_.-]')

pushd "${project_path}"
if [[ -f composer.json ]]; then
    # Make name lowercase to match latest composer naming rules
    sed -i 's/"name": "\(.*\)"/"name": "\L\1"/g' composer.json
    composer update --no-interaction
else
    composer init --no-interaction --name "deser/${project_name_spec}"
fi
composer install
popd
