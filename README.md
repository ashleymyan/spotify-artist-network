# Spotify Artist Network

Find the connection path between any two artists using the Last.fm similarity graph.

Given a start and target artist, the app builds a weighted graph (edges = Last.fm match scores)
and finds either the **shortest path** (fewest hops) or the **strongest path** (highest minimum
edge score), then displays top track recommendations at each stop.

---

## One-time setup

### 1. Get a Last.fm API key

1. Create a free account at <https://www.last.fm/>
2. Register an API application at <https://www.last.fm/api/account/create>
   - Application name: anything (e.g. "Artist Network")
   - Application description: anything
3. Copy the **API key** shown on the confirmation page

### 2. Download the JSON library

The project uses [org.json](https://github.com/stleary/JSON-java) to parse Last.fm responses.
Download the JAR into a `lib/` directory at the project root:

```bash
mkdir -p lib
curl -L "https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar" \
     -o lib/json.jar
```

### 3. Set your API key

```bash
export LASTFM_API_KEY=your_key_here
```

Add that line to your `~/.zshrc` (or `~/.bashrc`) to persist it across sessions.

---

## Compile

```bash
javac -cp lib/json.jar -d out \
  src/app/model/PathResult.java \
  src/app/graph/ArtistNode.java \
  src/app/graph/ArtistEdge.java \
  src/app/graph/ArtistGraph.java \
  src/app/api/LastFmClient.java \
  src/app/algorithm/StrongestPathFinder.java \
  src/app/service/ArtistConnectionService.java \
  src/app/Main.java
```

## Run

```bash
java -cp out:lib/json.jar app.Main
```

On Windows, replace `:` with `;` in the classpath.

---

## Project structure

```
src/app/
├── Main.java                        Entry point (mock test + live API demo)
├── model/
│   └── PathResult.java              Path DTO: artist list, edge scores, path score
├── graph/
│   ├── ArtistNode.java              Node: artist name + cached top tracks
│   ├── ArtistEdge.java              Directed edge: source → target, weight = match score
│   └── ArtistGraph.java             Weighted adjacency-list graph
├── api/
│   └── LastFmClient.java            Last.fm HTTP client (getSimilar, getTopTracks)
├── algorithm/
│   └── StrongestPathFinder.java     BFS (shortest) + max-min priority queue (strongest)
└── service/
    └── ArtistConnectionService.java BFS graph expansion + unified path-finding interface
```

---

## How it works

1. **Graph construction** (`ArtistConnectionService.buildGraph`):  
   BFS from the start artist, calling `artist.getSimilar` layer by layer up to `maxDepth`
   hops.  Each call returns up to 10 similar artists with match scores, which become
   weighted directed edges.  Already-fetched nodes are skipped (in-session cache).

2. **Shortest path** (`StrongestPathFinder.findShortestPath`):  
   Standard BFS; first path to reach the target wins.

3. **Strongest path** (`StrongestPathFinder.findStrongestPath`):  
   Max-heap priority queue sorted by current path score = min edge weight seen so far.
   Maximises the weakest link along the path.

4. **Top tracks** (`ArtistConnectionService.getTopTracks`):  
   Calls `artist.getTopTracks` on demand, caches result in the `ArtistNode`.

---

## Service API (for Person 2 / UI layer)

```java
ArtistConnectionService svc = new ArtistConnectionService(apiKey);

// Find connection path
PathResult path = svc.findStrongestPath("Taylor Swift", "Billie Eilish", 3);
// path.getArtists()    → ["Taylor Swift", "Lorde", "Billie Eilish"]
// path.getEdgeScores() → [0.91, 0.64]
// path.getPathScore()  → 0.64   (min edge = weakest link)
// path.isFound()       → true / false

// Top tracks at each stop
Map<String, List<String>> tracks = svc.getTopTracksForPath(path);
// tracks.get("Lorde") → ["Royals", "Tennis Court", "Team"]
```
