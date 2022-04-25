#!/usr/bin/env bash
echo --- Build image at $PWD
TAG=hivemq/hivemq-k8s-test:0.1
docker build -t $TAG -f broker.dockerfile .

echo --- Save File

docker save $TAG | gzip > hivemq-image.tgz