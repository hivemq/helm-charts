#!/usr/bin/env bash

# Get containers and save files
containers=("busybox:1.35.0" "hivemq/hivemq4:k8s-4.8.0" "hivemq/init-dns-wait:1.0.0" "hivemq/hivemq-operator:4.7.1")

#We split the function so we can build the images locally
function pull_images(){
  local local_containers=("${@}")
  for image in "${local_containers[@]}"; do
    image_name=("${image%:*}")
    image_name=("${image_name//\//_}")
    docker pull "${image}"
    docker tag "${image}" "${image_name[0]}:snapshot"
  done
}

function save_files(){
  local local_containers=("${@}")
  for image in "${local_containers[@]}"; do
    echo Saving "${image}"
    docker save "${image}" -o "${image_name[0]}.tar"
  done
}

pull_images "${containers[@]}"
save_files "${containers[@]}"