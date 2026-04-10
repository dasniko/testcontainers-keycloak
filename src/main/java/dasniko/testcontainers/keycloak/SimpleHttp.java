/*
 * Copyright (c) 2026 Niko Köbler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dasniko.testcontainers.keycloak;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal HTTP client helper, modelled after Keycloak's {@code org.keycloak.broker.provider.util.SimpleHttp}.
 * Uses only {@link java.net.http.HttpClient} — no additional dependencies.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class SimpleHttp {

    private final String method;
    private final String url;
    private SSLContext sslContext;
    private final Map<String, String> formParams = new LinkedHashMap<>();

    static SimpleHttp doGet(String url) {
        return new SimpleHttp("GET", url);
    }

    static SimpleHttp doPost(String url) {
        return new SimpleHttp("POST", url);
    }

    SimpleHttp sslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    SimpleHttp param(String name, String value) {
        formParams.put(name, value);
        return this;
    }

    Response asResponse() throws IOException {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder();
        if (sslContext != null) {
            clientBuilder.sslContext(sslContext);
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url));
        if (formParams.isEmpty()) {
            requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            String encodedBody = formParams.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                         + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
            requestBuilder
                .header("Content-Type", "application/x-www-form-urlencoded")
                .method(method, HttpRequest.BodyPublishers.ofString(encodedBody));
        }

        try {
            HttpResponse<String> response = clientBuilder.build().send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request was interrupted", e);
        }
    }

    @Getter
    @RequiredArgsConstructor
    static class Response {
        private final int status;
        private final String body;
    }
}
