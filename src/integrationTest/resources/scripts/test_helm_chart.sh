#!/usr/bin/env sh
# Script to import images and run the helm chart when doing manual testing
echo Import images

cd /build/ || exit
for i in *.tar;
do
  /bin/ctr images import "${i}"
done

echo Setup Helm Chart

cd /

/bin/helm dependency update /chart
/bin/helm --kubeconfig /etc/rancher/k3s/k3s.yaml install hivemq /chart -f /files/values.yml


