package app.model;

import java.util.List;

public class PathResult {
    private final List<String> artists;
    private final List<Double> edgeScores;
    private final double pathScore;
    private final boolean found;

    public PathResult(List<String> artists, List<Double> edgeScores, double pathScore, boolean found) {
        this.artists = artists;
        this.edgeScores = edgeScores;
        this.pathScore = pathScore;
        this.found = found;
    }

    public static PathResult notFound() {
        return new PathResult(List.of(), List.of(), 0.0, false);
    }

    public List<String> getArtists() {
        return artists;
    }

    public List<Double> getEdgeScores() {
        return edgeScores;
    }

    public double getPathScore() {
        return pathScore;
    }

    public boolean isFound() {
        return found;
    }

    @Override
    public String toString() {
        if (!found) {
            return "No path found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Path score: ").append(String.format("%.3f", pathScore)).append("\n");

        for (int i = 0; i < artists.size(); i++) {
            sb.append(artists.get(i));
            if (i < edgeScores.size()) {
                sb.append(" --(")
                  .append(String.format("%.3f", edgeScores.get(i)))
                  .append(")--> ");
            }
        }

        return sb.toString();
    }
}
