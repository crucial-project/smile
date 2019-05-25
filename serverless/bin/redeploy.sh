#!/usr/bin/env bash

./deploy-lambda.sh -delete
./deploy-lambda.sh -create
./deploy-creson.sh -delete
./deploy-creson.sh -create
