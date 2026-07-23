package me.acharliekelly.canvas.caption;

/**
 * Typed port to the replaceable caption worker. The backend retains job state and editorial
 * authority; a worker response is never an approved description.
 */
public interface CaptionClient {
    /**
     * Performs one worker call and does not retry. Transport or response-contract failures
     * propagate to caption job orchestration, which records the safe failure and decides whether
     * a later retry is appropriate. Returned text is draft material and its engine fields preserve
     * worker provenance.
     */
    CaptionResponse caption(CaptionRequest request);

    record CaptionRequest(String imageUrl, String title, String credit, String context) {}

    record CaptionResponse(String label, String text, String engine, String engineVersion) {}
}
