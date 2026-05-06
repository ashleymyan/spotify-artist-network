package app.service;

import app.algorithm.StrongestPathFinder;
import app.api.LastFmClient;
import app.api.LastFmClient.ArtistNotFoundException;
import app.graph.ArtistEdge;
import app.graph.ArtistGraph;
import app.graph.ArtistNode;
import app.model.PathResult;

import java.io.IOException;
import java.util.*;

/**
 * High-level service that Person 2's UI should call.
 *
 * It lazily builds the artist similarity graph by doing a BFS from the start
 * artist, fetching similar artists from Last.fm layer by layer, then runs the
 * path-finding algorithms on the populated graph.
 *
 * The graph is cached across calls within the same service instance, so
 * repeated queries for overlapping artists skip redundant API calls.
 *
 * Usage:
 *   ArtistConnectionService svc = new ArtistConnectionService(apiKey);
 *   PathResult path = svc.findStrongestPath("Taylor Swift", "Billie Eilish", 3);
 *   List<String> tracks = svc.getTopTracks("Lorde");
 */
public class ArtistConnectionService {

    /**
     * Hard cap on Last.fm API calls per search to prevent exponential explosion.
     * At 10 similar artists per call, 60 calls can reach ~2 hops from most artists.
     */
    private static final int MAX_API_CALLS_PER_SEARCH = 60;

    private final LastFmClient apiClient;
    private final ArtistGraph graph;
    private final StrongestPathFinder pathFinder;

    public ArtistConnectionService(String apiKey) {
        this.apiClient  = new LastFmClient(apiKey);
        this.graph      = new ArtistGraph();
        this.pathFinder = new StrongestPathFinder();
    }

    // -------------------------------------------------------------------------
    // Primary interface for Person 2
    // -------------------------------------------------------------------------

    /**
     * Finds the path with the highest minimum edge score between two artists
     * (i.e., the path whose weakest link is as strong as possible).
     *
     * @param startArtist  exact artist name as it appears on Last.fm
     * @param targetArtist exact artist name as it appears on Last.fm
     * @param maxDepth     max hops (1–6); higher values make more API calls
     * @return PathResult with artists, per-edge scores, and overall path score
     * @throws ArtistNotFoundException if either artist is not found on Last.fm
     * @throws IOException             on network errors
     * @throws InterruptedException    if the calling thread is interrupted
     */
    public PathResult findStrongestPath(String startArtist, String targetArtist, int maxDepth)
            throws IOException, InterruptedException {

        validateInputs(startArtist, targetArtist, maxDepth);
        buildGraph(startArtist, targetArtist, maxDepth);

        PathResult result = pathFinder.findStrongestPath(graph, startArtist, targetArtist, maxDepth);
        if (!result.isFound()) {
            return PathResult.notFound();
        }
        return result;
    }

    /**
     * Finds the fewest-hops path between two artists (BFS, ignores edge weights).
     *
     * @param startArtist  exact artist name as it appears on Last.fm
     * @param targetArtist exact artist name as it appears on Last.fm
     * @param maxDepth     max hops (1–6)
     * @return PathResult with artists, per-edge scores, and overall path score
     * @throws ArtistNotFoundException if either artist is not found on Last.fm
     * @throws IOException             on network errors
     * @throws InterruptedException    if the calling thread is interrupted
     */
    public PathResult findShortestPath(String startArtist, String targetArtist, int maxDepth)
            throws IOException, InterruptedException {

        validateInputs(startArtist, targetArtist, maxDepth);
        buildGraph(startArtist, targetArtist, maxDepth);

        PathResult result = pathFinder.findShortestPath(graph, startArtist, targetArtist, maxDepth);
        if (!result.isFound()) {
            return PathResult.notFound();
        }
        return result;
    }

    /**
     * Returns up to 3 top track names for an artist (fetched once then cached).
     *
     * @param artistName exact artist name
     * @return list of track name strings, may be empty if the API returns nothing
     * @throws IOException          on network errors
     * @throws InterruptedException if interrupted
     */
    public List<String> getTopTracks(String artistName)
            throws IOException, InterruptedException {

        if (artistName == null || artistName.isBlank()) {
            throw new IllegalArgumentException("Artist name must not be empty.");
        }

        ArtistNode node = graph.getNode(artistName);
        if (node != null && node.isTracksFetched()) {
            return node.getTopTracks();
        }

        List<String> tracks = apiClient.getTopTracks(artistName.trim());

        if (node == null) {
            node = new ArtistNode(artistName.trim());
            graph.addNode(node);
        }
        node.setTopTracks(tracks);

        return tracks;
    }

    /**
     * Convenience method: fetches and caches top tracks for every artist in a path.
     * Returns a map from artist name → list of track names.
     * Skips any artist whose tracks cannot be fetched (logs a warning).
     */
    public Map<String, List<String>> getTopTracksForPath(PathResult path)
            throws InterruptedException {

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String artist : path.getArtists()) {
            try {
                result.put(artist, getTopTracks(artist));
            } catch (IOException e) {
                System.err.println("Warning: could not fetch top tracks for \""
                        + artist + "\": " + e.getMessage());
                result.put(artist, Collections.emptyList());
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Graph construction (BFS expansion via Last.fm)
    // -------------------------------------------------------------------------

    /**
     * BFS from {@code start} up to {@code maxDepth} hops, populating the graph
     * with edges fetched from Last.fm.  Respects {@link #MAX_API_CALLS_PER_SEARCH}
     * to avoid runaway API usage.
     *
     * Nodes already in the graph (from a previous call) are not re-fetched.
     */
    private void buildGraph(String start, String target, int maxDepth)
            throws IOException, InterruptedException {

        // depth map: artist name (lower-case) → BFS depth from start
        Map<String, Integer> depthMap = new HashMap<>();
        Queue<String>        queue    = new LinkedList<>();
        int apiCalls = 0;

        String normalizedStart = start.trim();
        queue.add(normalizedStart);
        depthMap.put(normalizedStart.toLowerCase(), 0);

        while (!queue.isEmpty() && apiCalls < MAX_API_CALLS_PER_SEARCH) {
            String current = queue.poll();
            int    depth   = depthMap.getOrDefault(current.toLowerCase(), 0);

            if (depth >= maxDepth) {
                continue;
            }

            // Skip if we already fetched this node's neighbors in a prior search.
            if (graph.hasNeighborsLoaded(current)) {
                for (ArtistEdge edge : graph.getNeighbors(current)) {
                    String neighbor    = edge.getTarget();
                    String neighborKey = neighbor.toLowerCase();
                    if (!depthMap.containsKey(neighborKey)) {
                        depthMap.put(neighborKey, depth + 1);
                        queue.add(neighbor);
                    }
                }
                continue;
            }

            // Fetch from Last.fm (the start artist counts as a special case: a 404
            // here should surface as an exception so the caller can report it).
            List<ArtistEdge> edges;
            try {
                edges = apiClient.getSimilarArtists(current);
                apiCalls++;
            } catch (ArtistNotFoundException e) {
                // If the start artist itself is not found, re-throw.
                if (current.equalsIgnoreCase(start)) {
                    throw e;
                }
                // Otherwise just skip this intermediate node.
                System.err.println("Warning: skipping unknown artist \"" + current + "\".");
                continue;
            } catch (IOException e) {
                System.err.println("Warning: API error for \"" + current
                        + "\": " + e.getMessage() + " – skipping.");
                continue;
            }

            for (ArtistEdge edge : edges) {
                graph.addEdge(edge.getSource(), edge.getTarget(), edge.getWeight());
                String neighborKey = edge.getTarget().toLowerCase();
                if (!depthMap.containsKey(neighborKey)) {
                    depthMap.put(neighborKey, depth + 1);
                    queue.add(edge.getTarget());
                }
            }
        }

        // If the target was never found AND the start artist exists, the graph
        // simply doesn't contain a path — path-finder will return notFound().
        // But if the start artist has NO neighbors at all, it was likely not found.
        if (!graph.hasNeighborsLoaded(start)) {
            throw new ArtistNotFoundException(start);
        }
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private void validateInputs(String start, String target, int maxDepth) {
        if (start == null || start.isBlank()) {
            throw new IllegalArgumentException("Start artist must not be empty.");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Target artist must not be empty.");
        }
        if (maxDepth < 1 || maxDepth > 6) {
            throw new IllegalArgumentException(
                    "maxDepth must be between 1 and 6 (got " + maxDepth + ").");
        }
    }
}
