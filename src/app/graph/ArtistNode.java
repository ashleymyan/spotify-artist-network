package app.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an artist as a node in the artist similarity graph.
 * Caches top tracks so we only fetch them once per artist per session.
 */
public class ArtistNode {

    private final String name;
    private List<String> topTracks;
    private boolean tracksFetched;

    public ArtistNode(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Artist name must not be empty.");
        }
        this.name = name.trim();
        this.topTracks = new ArrayList<>();
        this.tracksFetched = false;
    }

    public String getName() {
        return name;
    }

    public List<String> getTopTracks() {
        return Collections.unmodifiableList(topTracks);
    }

    public boolean isTracksFetched() {
        return tracksFetched;
    }

    public void setTopTracks(List<String> tracks) {
        this.topTracks = new ArrayList<>(tracks);
        this.tracksFetched = true;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArtistNode)) return false;
        return name.equalsIgnoreCase(((ArtistNode) o).name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }
}
