#!/bin/bash

PACK_DIR=package;

publish() {
    cd $PACK_DIR
    echo 'Publishing to AzureDevops...'
    npm publish --"registry=https://pkgs.dev.azure.com/SolteqDKDigitalCommerce/_packaging/digitalcommerce/npm/registry/" *.tgz
}

./pack.sh && publish
