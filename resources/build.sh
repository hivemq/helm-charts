#!/usr/bin/env bash
echo --- Build image at $PWD
TAG=hivemq/hivemq-k8s-test:0.1
docker build -t $TAG .

echo --- Save File

docker save $TAG | gzip > hivemq-image.tgz

echo --- Move file
mv hivemq-image.tgz ../src/integrationTest/resources/