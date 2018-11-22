#!/usr/bin/env bash

echo "Preparing build.gradle"

LOCATION=`pwd`

function build_ui {
	echo "Sourcing nvm"
	source /home/bas/.nvm/nvm.sh
	echo "using node version 8.6.0"
	nvm install v8.6.0
	nvm use v8.6.0
	echo "Installing Yarn"
	npm install -g yarn
	echo "Installing JS deps in javascript"
	cd ./javascript
	yarn install
	echo "Running JS build.gradle"
	yarn run build
	echo "Destroying dependencies cache"
	rm -rf ./node_modules
}

build_ui