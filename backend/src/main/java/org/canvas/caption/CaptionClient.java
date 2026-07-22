package org.canvas.caption;

public interface CaptionClient {
    CaptionResponse caption(CaptionRequest request);

    record CaptionRequest(String imageUrl, String title, String credit, String context) {}

    record CaptionResponse(String label, String text, String engine, String engineVersion) {}
}
