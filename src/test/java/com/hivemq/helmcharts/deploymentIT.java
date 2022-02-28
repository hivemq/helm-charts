package com.hivemq.helmcharts;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.StringReader;


public class deploymentIT {
    @Test
    public void deployCluster() throws IOException, ApiException {
        K3sContainer k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.21.3-k3s1"));
        String kubeConfigYaml = k3s.getKubeConfigYaml();

        System.out.println(kubeConfigYaml);

        ApiClient client = Config.fromConfig(new StringReader(kubeConfigYaml));
        CoreV1Api api = new CoreV1Api(client);

        // interact with the running K3s server, e.g.:
        V1NodeList nodes = api.listNode(null, null, null, null, null, null, null, null, null, null);

    }
}
