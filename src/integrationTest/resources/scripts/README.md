# Scripts
## Containers
### To copy into the Testcontainer:

- busybox:1.35.0
- hivemq/hivemq4:k8s-4.8.0
- hivemq/init-dns-wait:1.0.0
- hivemq/hivemq-operator:4.7.1

### Using custom images:

When using custom images for the M1, all the images should be arm64 unless the k3s is running for an intel machine, .


