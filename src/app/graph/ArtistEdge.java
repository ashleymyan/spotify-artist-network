package app.graph;

/**
 * A directed, weighted edge in the artist similarity graph.
 * The weight is the Last.fm match score (0.0–1.0), where higher means more similar.
 */
public class ArtistEdge {

    private final String source;
    private final String target;
    private final double weight;

    public ArtistEdge(String source, String target, double weight) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Edge source must not be empty.");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Edge target must not be empty.");
        }
        if (weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("Edge weight must be between 0.0 and 1.0, got: " + weight);
        }
        this.source = source.trim();
        this.target = target.trim();
        this.weight = weight;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    /** Last.fm match score: 0.0 (unrelated) to 1.0 (identical). */
    public double getWeight() {
        return weight;
    }

    @Override
    public String toString() {
        return source + " --(" + String.format("%.3f", weight) + ")--> " + target;
    }
}
