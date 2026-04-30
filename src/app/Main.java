package app;

import app.algorithm.StrongestPathFinder;
import app.graph.ArtistEdge;
import app.graph.ArtistGraph;
import app.model.PathResult;

public class Main {
    public static void main(String[] args) {
        System.out.println("Spotify Artist Network - Algorithm Test\n");

        // Build mock graph
        ArtistGraph graph = new ArtistGraph();

        graph.addEdge("Taylor Swift", "Lorde", 0.91);
        graph.addEdge("Lorde", "Billie Eilish", 0.64);

        graph.addEdge("Taylor Swift", "Ed Sheeran", 0.55);
        graph.addEdge("Ed Sheeran", "Billie Eilish", 0.80);

        graph.addEdge("Lorde", "Lana Del Rey", 0.70);
        graph.addEdge("Lana Del Rey", "Billie Eilish", 0.60);

        StrongestPathFinder finder = new StrongestPathFinder();

        String start = "Taylor Swift";
        String target = "Billie Eilish";
        int maxDepth = 3;

        // Shortest path
        System.out.println("=== Shortest Path ===");
        PathResult shortest = finder.findShortestPath(graph, start, target, maxDepth);
        System.out.println(shortest + "\n");

        // Strongest path
        System.out.println("=== Strongest Path ===");
        PathResult strongest = finder.findStrongestPath(graph, start, target, maxDepth);
        System.out.println(strongest + "\n");

        // No path test
        System.out.println("=== No Path Test ===");
        PathResult noPath = finder.findStrongestPath(graph, "Taylor Swift", "Drake", 2);
        System.out.println(noPath);
    }
}
