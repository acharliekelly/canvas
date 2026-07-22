package org.canvas.caption;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCaptionClient implements CaptionClient {
    private final RestClient client;

    HttpCaptionClient(@Value("${canvas.caption-worker-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(5));
        requests.setReadTimeout(Duration.ofSeconds(15));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }

    @Override
    public CaptionResponse caption(CaptionRequest request) {
        return client.post()
                .uri("/captions")
                .body(request)
                .retrieve()
                .body(CaptionResponse.class);
    }
}
