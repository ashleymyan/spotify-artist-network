package app.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import app.api.LastFmClient.ArtistNotFoundException;
import app.model.PathResult;
import app.service.ArtistConnectionService;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathHandler implements HttpHandler {

    private final ArtistConnectionService service;

    public PathHandler(ArtistConnectionService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Only allow GET
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            ApiServer.sendResponse(exchange, 405, "application/json",
                    "{\"error\":\"Method not allowed\"}");
            return;
        }

        // Parse query string
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        String start  = params.get("start");
        String target = params.get("target");
        String depthStr = params.getOrDefault("depth", "3");
        String mode   = params.getOrDefault("mode", "strongest");

        // Validate required params
        if (start == null || start.isBlank()) {
            ApiServer.sendResponse(exchange, 400, "application/json",
                    "{\"error\":\"Missing required parameter: start\"}");
            return;
        }
        if (target == null || target.isBlank()) {
            ApiServer.sendResponse(exchange, 400, "application/json",
                    "{\"error\":\"Missing required parameter: target\"}");
            return;
        }

        int depth;
        try {
            depth = Integer.parseInt(depthStr);
            if (depth < 1 || depth > 6) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            ApiServer.sendResponse(exchange, 400, "application/json",
                    "{\"error\":\"depth must be an integer between 1 and 6\"}");
            return;
        }

        // Run the path search
        try {
            PathResult result = mode.equalsIgnoreCase("shortest")
                    ? service.findShortestPath(start, target, depth)
                    : service.findStrongestPath(start, target, depth);

            String json;
            if (!result.isFound()) {
                json = buildNotFoundJson(start, target);
            } else {
                Map<String, List<String>> tracks = service.getTopTracksForPath(result);
                json = buildFoundJson(result, tracks);
            }

            ApiServer.sendResponse(exchange, 200, "application/json", json);

        } catch (ArtistNotFoundException e) {
            String msg = escape(e.getMessage());
            ApiServer.sendResponse(exchange, 404, "application/json",
                    "{\"error\":\"" + msg + "\",\"artistName\":\"" + escape(e.getArtistName()) + "\"}");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ApiServer.sendResponse(exchange, 500, "application/json",
                    "{\"error\":\"Request was interrupted\"}");
        } catch (Exception e) {
            String msg = escape(e.getMessage() != null ? e.getMessage() : "Unknown error");
            ApiServer.sendResponse(exchange, 500, "application/json",
                    "{\"error\":\"" + msg + "\"}");
        }
    }

    private String buildFoundJson(PathResult result, Map<String, List<String>> tracks) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"found\":true,");
        sb.append("\"pathScore\":").append(String.format("%.4f", result.getPathScore())).append(",");

        // Artists array
        sb.append("\"artists\":[");
        List<String> artists = result.getArtists();
        for (int i = 0; i < artists.size(); i++) {
            sb.append("\"").append(escape(artists.get(i))).append("\"");
            if (i < artists.size() - 1) sb.append(",");
        }
        sb.append("],");

        // Edge scores array
        sb.append("\"edgeScores\":[");
        List<Double> scores = result.getEdgeScores();
        for (int i = 0; i < scores.size(); i++) {
            sb.append(String.format("%.4f", scores.get(i)));
            if (i < scores.size() - 1) sb.append(",");
        }
        sb.append("],");

        // Tracks map
        sb.append("\"tracks\":{");
        int artistIdx = 0;
        for (Map.Entry<String, List<String>> entry : tracks.entrySet()) {
            sb.append("\"").append(escape(entry.getKey())).append("\":[");
            List<String> trackList = entry.getValue();
            for (int i = 0; i < trackList.size(); i++) {
                sb.append("\"").append(escape(trackList.get(i))).append("\"");
                if (i < trackList.size() - 1) sb.append(",");
            }
            sb.append("]");
            if (artistIdx < tracks.size() - 1) sb.append(",");
            artistIdx++;
        }
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    private String buildNotFoundJson(String start, String target) {
        return "{\"found\":false,\"error\":\"No path found between \\\""
                + escape(start) + "\\\" and \\\"" + escape(target) + "\\\"\"}";
    }


    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String key   = URLDecoder.decode(kv[0], StandardCharsets.UTF_8).trim();
                String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8).trim();
                map.put(key, value);
            }
        }
        return map;
    }


    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}