package app.algorithm;

import app.graph.ArtistEdge;
import app.graph.ArtistGraph;
import app.model.PathResult;

import java.util.*;

public class StrongestPathFinder {

    /**
     * Finds the unweighted shortest path between two artists using BFS.
     * Respects maxDepth, where maxDepth = max number of edges allowed.
     */
    public PathResult findShortestPath(ArtistGraph graph, String start, String target, int maxDepth) {
        if (start == null || target == null || graph == null || maxDepth < 0) {
            return PathResult.notFound();
        }

        if (start.equalsIgnoreCase(target)) {
            return new PathResult(List.of(start), List.of(), 1.0, true);
        }

        Queue<PathState> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.add(new PathState(
                start,
                new ArrayList<>(List.of(start)),
                new ArrayList<>(),
                1.0
        ));

        visited.add(normalize(start));

        while (!queue.isEmpty()) {
            PathState curr = queue.poll();

            if (curr.edgeScores.size() >= maxDepth) {
                continue;
            }

            for (ArtistEdge edge : graph.getNeighbors(curr.artist)) {
                String next = edge.getTarget();

                if (visited.contains(normalize(next))) {
                    continue;
                }

                List<String> newPath = new ArrayList<>(curr.path);
                newPath.add(next);

                List<Double> newScores = new ArrayList<>(curr.edgeScores);
                newScores.add(edge.getWeight());

                double newPathScore = minScore(newScores);

                if (next.equalsIgnoreCase(target)) {
                    return new PathResult(newPath, newScores, newPathScore, true);
                }

                visited.add(normalize(next));
                queue.add(new PathState(next, newPath, newScores, newPathScore));
            }
        }

        return PathResult.notFound();
    }

    /**
     * Finds the "strongest" path between two artists.
     *
     * Path strength is defined as the minimum edge score along the path.
     * This avoids choosing paths that have one very weak connection.
     *
     * Example:
     * Path A scores: 0.9, 0.8, 0.7 => strength 0.7
     * Path B scores: 0.99, 0.95, 0.2 => strength 0.2
     * Path A is stronger.
     */
    public PathResult findStrongestPath(ArtistGraph graph, String start, String target, int maxDepth) {
        if (start == null || target == null || graph == null || maxDepth < 0) {
            return PathResult.notFound();
        }

        if (start.equalsIgnoreCase(target)) {
            return new PathResult(List.of(start), List.of(), 1.0, true);
        }

        PriorityQueue<PathState> pq = new PriorityQueue<>(
                (a, b) -> Double.compare(b.pathScore, a.pathScore)
        );

        Map<String, Double> bestScoreSeen = new HashMap<>();

        pq.add(new PathState(
                start,
                new ArrayList<>(List.of(start)),
                new ArrayList<>(),
                1.0
        ));

        bestScoreSeen.put(normalize(start), 1.0);

        while (!pq.isEmpty()) {
            PathState curr = pq.poll();

            if (curr.artist.equalsIgnoreCase(target)) {
                return new PathResult(curr.path, curr.edgeScores, curr.pathScore, true);
            }

            if (curr.edgeScores.size() >= maxDepth) {
                continue;
            }

            for (ArtistEdge edge : graph.getNeighbors(curr.artist)) {
                String next = edge.getTarget();

                if (curr.pathContains(next)) {
                    continue;
                }

                double newPathScore = Math.min(curr.pathScore, edge.getWeight());

                String key = normalize(next);
                if (bestScoreSeen.containsKey(key) && bestScoreSeen.get(key) >= newPathScore) {
                    continue;
                }

                bestScoreSeen.put(key, newPathScore);

                List<String> newPath = new ArrayList<>(curr.path);
                newPath.add(next);

                List<Double> newScores = new ArrayList<>(curr.edgeScores);
                newScores.add(edge.getWeight());

                pq.add(new PathState(next, newPath, newScores, newPathScore));
            }
        }

        return PathResult.notFound();
    }

    private double minScore(List<Double> scores) {
        if (scores.isEmpty()) {
            return 1.0;
        }

        double min = Double.MAX_VALUE;
        for (double score : scores) {
            min = Math.min(min, score);
        }
        return min;
    }

    private String normalize(String s) {
        return s.trim().toLowerCase();
    }

    private static class PathState {
        String artist;
        List<String> path;
        List<Double> edgeScores;
        double pathScore;

        PathState(String artist, List<String> path, List<Double> edgeScores, double pathScore) {
            this.artist = artist;
            this.path = path;
            this.edgeScores = edgeScores;
            this.pathScore = pathScore;
        }

        boolean pathContains(String artist) {
            for (String a : path) {
                if (a.equalsIgnoreCase(artist)) {
                    return true;
                }
            }
            return false;
        }
    }
}
