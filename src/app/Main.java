package app;

import app.algorithm.StrongestPathFinder;
import app.api.LastFmClient.ArtistNotFoundException;
import app.graph.ArtistGraph;
import app.model.PathResult;
import app.service.ArtistConnectionService;

import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws Exception {

        if (args.length > 0 && args[0].equals("--server")) {
            String apiKey = System.getenv("LASTFM_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                System.err.println("Error: set LASTFM_API_KEY before starting the server.");
                System.exit(1);
            }
            app.server.ApiServer server = new app.server.ApiServer(8080, apiKey);
            server.start();
            Thread.currentThread().join();
            return;
        }

        // ------------------------------------------------------------------ //
        //  SECTION 1: Mock-graph test (no API key required)                   //
        //  Verifies that the graph data structure and path-finding algorithms  //
        //  work correctly on a small, hand-built graph.                       //
        // ------------------------------------------------------------------ //


        System.out.println("==============================================");
        System.out.println("  SECTION 1: Mock Graph (no API key needed)  ");
        System.out.println("==============================================\n");

        ArtistGraph graph = new ArtistGraph();

        graph.addEdge("Taylor Swift", "Lorde",        0.91);
        graph.addEdge("Lorde",        "Billie Eilish", 0.64);

        graph.addEdge("Taylor Swift", "Ed Sheeran",   0.55);
        graph.addEdge("Ed Sheeran",   "Billie Eilish", 0.80);

        graph.addEdge("Lorde",        "Lana Del Rey", 0.70);
        graph.addEdge("Lana Del Rey", "Billie Eilish", 0.60);

        StrongestPathFinder finder = new StrongestPathFinder();
        String start  = "Taylor Swift";
        String target = "Billie Eilish";
        int    depth  = 3;

        System.out.println("--- Shortest Path (fewest hops) ---");
        PathResult shortest = finder.findShortestPath(graph, start, target, depth);
        System.out.println(shortest);

        System.out.println("\n--- Strongest Path (highest min-edge score) ---");
        PathResult strongest = finder.findStrongestPath(graph, start, target, depth);
        System.out.println(strongest);

        System.out.println("\n--- No-path test (Drake not in graph) ---");
        PathResult noPath = finder.findStrongestPath(graph, "Taylor Swift", "Drake", 2);
        System.out.println(noPath);

        // ------------------------------------------------------------------ //
        //  SECTION 2: Live Last.fm API demo                                   //
        //  Runs only when LASTFM_API_KEY is set as an environment variable.   //
        //  Set it with:  export LASTFM_API_KEY=your_key_here                  //
        // ------------------------------------------------------------------ //

        String apiKey = System.getenv("LASTFM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("\n==============================================");
            System.out.println("  SECTION 2: Live API demo — SKIPPED         ");
            System.out.println("  Set LASTFM_API_KEY to enable it.            ");
            System.out.println("==============================================");
            return;
        }

        System.out.println("\n==============================================");
        System.out.println("  SECTION 2: Live Last.fm API Demo            ");
        System.out.println("==============================================\n");

        ArtistConnectionService service = new ArtistConnectionService(apiKey);

        String liveStart  = "Taylor Swift";
        String liveTarget = "Billie Eilish";
        int    liveDepth  = 3;

        System.out.printf("Searching: \"%s\" → \"%s\" (max %d hops)%n%n",
                liveStart, liveTarget, liveDepth);

        try {
            PathResult liveResult = service.findStrongestPath(liveStart, liveTarget, liveDepth);

            if (!liveResult.isFound()) {
                System.out.println("No path found within " + liveDepth + " hops.");
            } else {
                System.out.println("Strongest path found!");
                System.out.println(liveResult);

                // Fetch top tracks for each artist in the path.
                System.out.println("\nTop tracks along the path:");
                Map<String, List<String>> trackMap =
                        service.getTopTracksForPath(liveResult);

                trackMap.forEach((artist, tracks) -> {
                    System.out.println("  " + artist + ": " +
                            (tracks.isEmpty() ? "(none)" : String.join(", ", tracks)));
                });
            }

        } catch (ArtistNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
