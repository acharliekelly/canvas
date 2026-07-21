package org.canvas.artwork;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "canvas.upload-max-size=1KB")
class ArtworkMultipartHttpTest {
    private static final String BOUNDARY = "canvas-http-boundary";

    @LocalServerPort int port;
    @Test
    void oversizedMultipartRequestCrossesTheServletParserAndReturnsProblemDetails() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String anonymousCsrf = csrfToken(send(client, request("/api/session").GET()).body());

        String loginBody = "username=admin&password=password&_csrf=" + anonymousCsrf;
        HttpResponse<String> login = send(client, request("/api/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(loginBody)));
        assertThat(login.statusCode()).isEqualTo(200);

        String authenticatedCsrf = csrfToken(send(client, request("/api/session").GET()).body());
        byte[] body = multipartBody(new byte[3 * 1024 * 1024]);
        HttpResponse<String> response = send(client, request("/api/artworks")
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header("X-CSRF-TOKEN", authenticatedCsrf)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"code\":\"file_too_large\"");
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json, application/problem+json");
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String csrfToken(String sessionBody) {
        return sessionBody.replaceAll(".*\\\"csrfToken\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private static byte[] multipartBody(byte[] image) {
        byte[] prefix = ("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"title\"\r\n\r\nBlue Study\r\n"
                + "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"credit\"\r\n\r\nA. Artist\r\n"
                + "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"large.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefix.length + image.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(image, 0, body, prefix.length, image.length);
        System.arraycopy(suffix, 0, body, prefix.length + image.length, suffix.length);
        return body;
    }

    @TestConfiguration
    static class StorageStubConfiguration {
        @Bean
        @Primary
        ObjectStorage objectStorageStub() {
            return new ObjectStorage() {
                @Override
                public StoredObject put(java.io.InputStream content, long byteSize, String mediaType) {
                    return new StoredObject("unused");
                }

                @Override
                public void delete(String objectKey) {
                }
            };
        }
    }
}
