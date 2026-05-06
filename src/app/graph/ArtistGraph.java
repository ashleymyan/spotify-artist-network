package app.graph;

import java.util.*;

/**
 * Weighted, directed adjacency-list graph of artists.
 *
 * Nodes  = ArtistNode (one per unique artist name, case-insensitive)
 * Edges  = ArtistEdge with a Last.fm match score as the weight
 *
 * All artist name lookups are case-insensitive so "Taylor Swift" and
 * "taylor swift" resolve to the same node.
 */
public class ArtistGraph {

    private final Map<String, ArtistNode> nodes;
    private final Map<String, List<ArtistEdge>> adjacency;

    public ArtistGraph() {
        this.nodes = new HashMap<>();
        this.adjacency = new HashMap<>();
    }

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    /**
     * Inserts a pre-built node. No-op if a node with the same name already exists.
     */
    public void addNode(ArtistNode node) {
        String key = normalize(node.getName());
        nodes.putIfAbsent(key, node);
        adjacency.putIfAbsent(key, new ArrayList<>());
    }

    /**
     * Adds a directed edge source → target with the given weight.
     * Creates both nodes if they don't exist yet.
     * If an edge between the same pair already exists, the one with the higher weight is kept.
     */
    public void addEdge(String source, String target, double weight) {
        ensureNode(source);
        ensureNode(target);

        String sourceKey = normalize(source);
        List<ArtistEdge> neighbors = adjacency.get(sourceKey);

        // Remove any existing edge to `target` so we can replace with a better weight.
        neighbors.removeIf(e -> normalize(e.getTarget()).equals(normalize(target))
                && e.getWeight() < weight);

        boolean alreadyExists = neighbors.stream()
                .anyMatch(e -> normalize(e.getTarget()).equals(normalize(target)));

        if (!alreadyExists) {
            neighbors.add(new ArtistEdge(source, target, weight));
        }
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** Returns the outgoing edges for the given artist, or an empty list if unknown. */
    public List<ArtistEdge> getNeighbors(String artistName) {
        List<ArtistEdge> edges = adjacency.get(normalize(artistName));
        return edges != null ? Collections.unmodifiableList(edges) : Collections.emptyList();
    }

    /** Returns the ArtistNode for the given name, or null if not in the graph. */
    public ArtistNode getNode(String artistName) {
        return nodes.get(normalize(artistName));
    }

    /** True if this artist name has been added to the graph (as a node). */
    public boolean containsNode(String artistName) {
        return nodes.containsKey(normalize(artistName));
    }

    /**
     * True if the artist's similar-artist edges have already been loaded from the API.
     * Used by ArtistConnectionService to avoid duplicate API calls.
     */
    public boolean hasNeighborsLoaded(String artistName) {
        String key = normalize(artistName);
        return adjacency.containsKey(key) && !adjacency.get(key).isEmpty();
    }

    /** Returns all artist name keys (lower-case) currently in the graph. */
    public Set<String> getAllArtistKeys() {
        return Collections.unmodifiableSet(nodes.keySet());
    }

    /** Total number of nodes. */
    public int nodeCount() {
        return nodes.size();
    }

    /** Total number of directed edges. */
    public int edgeCount() {
        return adjacency.values().stream().mapToInt(List::size).sum();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void ensureNode(String name) {
        String key = normalize(name);
        nodes.putIfAbsent(key, new ArtistNode(name));
        adjacency.putIfAbsent(key, new ArrayList<>());
    }

    private String normalize(String name) {
        return name.trim().toLowerCase();
    }
}
