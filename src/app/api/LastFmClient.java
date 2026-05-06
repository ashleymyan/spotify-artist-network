package app.api;

import app.graph.ArtistEdge;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thin HTTP wrapper around two Last.fm API methods:
 *   - artist.getSimilar  → weighted edges for the artist graph
 *   - artist.getTopTracks → track recommendations shown in the UI
 *
 * Construct with your API key. Each instance owns a single HttpClient.
 */
public class LastFmClient {

    private static final String BASE_URL = "https://ws.audioscrobbler.com/2.0/";

    /** How many similar artists to fetch per call. Keeping this small limits API call explosion. */
    private static final int SIMILAR_LIMIT = 10;

    /** How many top tracks to fetch per artist. */
    private static final int TRACKS_LIMIT = 3;

    private final String apiKey;
    private final HttpClient httpClient;

    public LastFmClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Last.fm API key must not be empty.");
        }
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Calls artist.getSimilar and returns a list of directed edges
     * from {@code artistName} to each similar artist, weighted by the match score.
     *
     * @throws ArtistNotFoundException if Last.fm does not recognise the artist name
     * @throws IOException             on any HTTP or network error
     * @throws InterruptedException    if the request thread is interrupted
     */
    public List<ArtistEdge> getSimilarArtists(String artistName)
            throws IOException, InterruptedException {

        String url = BASE_URL
                + "?method=artist.getsimilar"
                + "&artist=" + encode(artistName)
                + "&limit=" + SIMILAR_LIMIT
                + "&api_key=" + apiKey
                + "&format=json";

        String body = get(url);
        return parseSimilarArtists(artistName, body);
    }

    /**
     * Calls artist.getTopTracks and returns up to {@value #TRACKS_LIMIT} track names.
     *
     * @throws ArtistNotFoundException if Last.fm does not recognise the artist name
     * @throws IOException             on any HTTP or network error
     * @throws InterruptedException    if the request thread is interrupted
     */
    public List<String> getTopTracks(String artistName)
            throws IOException, InterruptedException {

        String url = BASE_URL
                + "?method=artist.gettoptracks"
                + "&artist=" + encode(artistName)
                + "&limit=" + TRACKS_LIMIT
                + "&api_key=" + apiKey
                + "&format=json";

        String body = get(url);
        return parseTopTracks(artistName, body);
    }

    // -------------------------------------------------------------------------
    // HTTP
    // -------------------------------------------------------------------------

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException(
                    "HTTP " + response.statusCode() + " from Last.fm for URL: " + url);
        }

        return response.body();
    }

    // -------------------------------------------------------------------------
    // Parsers
    // -------------------------------------------------------------------------

    private List<ArtistEdge> parseSimilarArtists(String sourceArtist, String json)
            throws IOException {
        JSONObject root = parseJson(json);
        checkForApiError(root, sourceArtist);

        JSONObject similar = root.optJSONObject("similarartists");
        if (similar == null) {
            return Collections.emptyList();
        }

        // Last.fm returns an empty string instead of an array when there are no results.
        Object artistsRaw = similar.opt("artist");
        if (!(artistsRaw instanceof JSONArray)) {
            return Collections.emptyList();
        }

        JSONArray artists = (JSONArray) artistsRaw;
        List<ArtistEdge> edges = new ArrayList<>();

        for (int i = 0; i < artists.length(); i++) {
            JSONObject artist = artists.optJSONObject(i);
            if (artist == null) continue;

            String name  = artist.optString("name",  "").trim();
            double match = parseDouble(artist.optString("match", "0"));

            if (!name.isEmpty() && match > 0.0) {
                edges.add(new ArtistEdge(sourceArtist, name, clamp(match)));
            }
        }

        return edges;
    }

    private List<String> parseTopTracks(String artistName, String json)
            throws IOException {
        JSONObject root = parseJson(json);
        checkForApiError(root, artistName);

        JSONObject topTracks = root.optJSONObject("toptracks");
        if (topTracks == null) {
            return Collections.emptyList();
        }

        Object tracksRaw = topTracks.opt("track");
        if (!(tracksRaw instanceof JSONArray)) {
            return Collections.emptyList();
        }

        JSONArray tracks = (JSONArray) tracksRaw;
        List<String> names = new ArrayList<>();

        for (int i = 0; i < tracks.length(); i++) {
            JSONObject track = tracks.optJSONObject(i);
            if (track == null) continue;

            String name = track.optString("name", "").trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }

        return names;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Checks the parsed JSON root for a Last.fm error object and throws the appropriate
     * exception.  Error code 6 = "Artist not found", others become generic IOExceptions.
     */
    private void checkForApiError(JSONObject root, String artistName) throws IOException {
        if (!root.has("error")) return;

        int code       = root.optInt("error", -1);
        String message = root.optString("message", "unknown error");

        if (code == 6) {
            throw new ArtistNotFoundException(artistName);
        }
        throw new IOException("Last.fm API error " + code + ": " + message);
    }

    private JSONObject parseJson(String json) throws IOException {
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new IOException("Could not parse Last.fm response as JSON: " + e.getMessage(), e);
        }
    }

    private String encode(String s) {
        return URLEncoder.encode(s.trim(), StandardCharsets.UTF_8);
    }

    private double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Clamps a match score to [0.0, 1.0] in case the API returns out-of-range values. */
    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // -------------------------------------------------------------------------
    // Checked exception for "artist not found"
    // -------------------------------------------------------------------------

    /** Thrown when Last.fm returns error code 6 (artist not found). */
    public static class ArtistNotFoundException extends IOException {
        private final String artistName;

        public ArtistNotFoundException(String artistName) {
            super("Artist not found on Last.fm: \"" + artistName + "\"");
            this.artistName = artistName;
        }

        public String getArtistName() {
            return artistName;
        }
    }
}
