# Artist Network

**Team:** Ashley Yan (asyan@seas.upenn.edu), Emily Yu (yuemily@seas.upenn.edu), Isabelle Gu (igu@wharton.upenn.edu)

## Project Description

Artist Network is a web application that finds the connection path between any two musical artists using the Last.fm similarity graph. Users enter a start and target artist, and the app builds a weighted graph where nodes are artists and edges represent Last.fm similarity scores. It then finds either the shortest path (fewest hops) or the strongest path (the chain whose weakest similarity link is as strong as possible), and displays the top three tracks for each artist along the way. The result is an interactive way to explore how seemingly different artists are musically connected through a chain of intermediaries.

## Categories Covered

This project implements concepts from two of the course categories: **Graph and Graph Algorithms** and **Social Networks**. The core of the project is a dynamically constructed weighted graph populated via BFS expansion through the Last.fm API, on which we run two different path-finding algorithms — standard BFS for shortest path and a modified max-min priority queue search for strongest path. The social networks aspect comes from modeling the Last.fm similarity graph as a network of artists connected by listener-driven similarity relationships, where path-finding reveals how artists relate to each other through shared audiences and musical influence.

## Work Breakdown

**Emily Yu — Data and Graph Layer**
Registered the Last.fm API key and built the API client calls. Designed and implemented the graph data structures using a weighted adjacency list, and handled API errors and edge cases such as unknown artists.

**Ashley Yan — Algorithms**
Implemented the path-finding algorithms, including BFS for the shortest path and a modified priority-queue-based search that maximizes the minimum edge weight for the strongest path. Built the higher-level service layer that lazily expands the graph through BFS up to a configurable depth limit, and integrated track lookups into the path results.

**Isabelle Gu — UI and Integration**
Built the frontend interface with search inputs, a path mode toggle, a max hops slider, and a path visualization component that displays similarity scores and top tracks for each artist. Built a lightweight Java HTTP server and request handler to expose the backend as a REST API and serve the frontend, then integrated the work of all three team members into the full working application.

## AI Usage

AI assistance (Claude) was used for the following:

- **Frontend development:** The full `src/resources/index.html` file, including HTML structure, CSS styling, and JavaScript for the path visualization, was generated with Claude's help. Claude was used iteratively to refine the design, color palette (Spotify-inspired dark and light modes), font choices, and interaction patterns based on feedback.

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

## Service API

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

---

## Running the Web UI

After compiling (see above), start the server with:

```bash
java -cp out:lib/json.jar app.Main --server
```

Then open **http://localhost:8080** in your browser.

### Compile command (includes server files)

```bash
javac -cp lib/json.jar -d out \
  src/app/model/PathResult.java \
  src/app/graph/ArtistNode.java \
  src/app/graph/ArtistEdge.java \
  src/app/graph/ArtistGraph.java \
  src/app/api/LastFmClient.java \
  src/app/algorithm/StrongestPathFinder.java \
  src/app/service/ArtistConnectionService.java \
  src/app/server/PathHandler.java \
  src/app/server/ApiServer.java \
  src/app/Main.java
```

Copy the frontend to the output directory so it can be served:

```bash
cp src/resources/index.html out/
```

### API endpoint

```
GET /api/path?start=Taylor+Swift&target=Billie+Eilish&depth=3&mode=strongest
```

| Param  | Required | Default     | Notes                        |
|--------|----------|-------------|------------------------------|
| start  | yes      |             | Artist name (Last.fm exact)  |
| target | yes      |             | Artist name (Last.fm exact)  |
| depth  | no       | 3           | 1–6 hops                     |
| mode   | no       | strongest   | "strongest" or "shortest"    |