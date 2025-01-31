package com.hivemq.helmcharts.util;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static io.restassured.RestAssured.given;

public class RestAPIUtil {

    private static final @NotNull String REST_API_DEFAULT_USER = "user";
    private static final @NotNull String REST_API_DEFAULT_PASSWORD = "password";
    private static final @NotNull String REST_API_DEFAULT_ENDPOINT_PATH = "/api/v1/mqtt/clients";

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(RestAPIUtil.class);

    private RestAPIUtil() {
    }

    public static @NotNull String createBasicAuthHeader(
            final @NotNull String username, final @NotNull String password) {
        final var credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public static void assertAuth(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String serviceName,
            final int port) throws Exception {
        assertAuth(client,
                namespace,
                serviceName,
                port,
                REST_API_DEFAULT_ENDPOINT_PATH,
                REST_API_DEFAULT_USER,
                REST_API_DEFAULT_PASSWORD,
                HttpStatus.SC_OK);
    }

    public static void assertAuth(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String serviceName,
            final int port,
            final @NotNull String username,
            final @NotNull String password,
            final int expectedHttpCode) throws Exception {
        assertAuth(client,
                namespace,
                serviceName,
                port,
                REST_API_DEFAULT_ENDPOINT_PATH,
                username,
                password,
                expectedHttpCode);
    }

    public static void assertAuth(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String serviceName,
            final int port,
            final @NotNull String endpointPath,
            final int expectedHttpCode) throws Exception {
        assertAuth(client,
                namespace,
                serviceName,
                port,
                endpointPath,
                REST_API_DEFAULT_USER,
                REST_API_DEFAULT_PASSWORD,
                expectedHttpCode);
    }

    public static void assertAuth(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String serviceName,
            final int port,
            final @NotNull String endpointPath,
            final @NotNull String username,
            final @NotNull String password,
            final int expectedHttpCode) throws Exception {
        LOG.info("Checking authorization for REST API on {}:{} (username: {}) (password: {}) (endpoint path: {})",
                serviceName,
                port,
                username,
                password,
                endpointPath);
        try (final var forwarded = K8sUtil.getPortForward(client, namespace, serviceName, port)) {
            final var baseRestApiEndpoint = "http://localhost:" + forwarded.getLocalPort();

            given().header("Authorization", createBasicAuthHeader(username, password))
                    .when()
                    .get(new URI(baseRestApiEndpoint + endpointPath).toURL())
                    .then()
                    .statusCode(expectedHttpCode)
                    .log()
                    .ifError();
        }
    }

    public static @NotNull List<Object> getAllMqttClients(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String serviceName,
            final int port) throws IOException {
        final List<Object> clients = new ArrayList<>(0);
        try (final var forwarded = K8sUtil.getPortForward(client, namespace, serviceName, port)) {
            final var baseRestApiEndpoint = "http://localhost:" + forwarded.getLocalPort();

            final var body = given().when()
                    .get(baseRestApiEndpoint + REST_API_DEFAULT_ENDPOINT_PATH)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .log()
                    .ifError()
                    .extract()
                    .body();
            clients.addAll(body.jsonPath().getList("items"));
        }
        return clients;
    }
}
