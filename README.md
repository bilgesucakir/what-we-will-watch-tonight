# what-we-will-watch-tonight

Spring Boot and Vue-based watchlist tool for Jsoup-driven Letterboxd
scraping, cross-user intersection matching, and CSV list export.

Scrapes public Letterboxd watchlist pages with Jsoup and helps you pick
something to watch, in two modes (two tabs in the UI):

- **Just Me** — a random pick from one person's own watchlist
- **Me & a Friend** — a random pick from what two people's watchlists have
  in common, or the full overlap as a browsable list

Both modes hand back a single random pick by default — for when you just
want an answer, not a list to argue about — with the full film list still
one click away, plus a CSV export ready to import into a new Letterboxd
list.

## Features

- **Random pick, single or shared** — the primary action in both tabs:
  picks one random film (from one watchlist, or from the overlap of two)
  and shows it front and center, with a poster from TMDB where one's
  found. Only the picked film gets a poster lookup
- **"Return all films"** — a smaller secondary action in both tabs for
  browsing the full list as a poster grid, sorted alphabetically, each
  linking to its Letterboxd page
- **Live username validation** — as you type, checks whether each username
  exists on Letterboxd and whether its watchlist is public, with the
  buttons staying disabled until ready, and shows the user's avatar once
  verified
- **CSV export** — from the full-list view, download the results as CSV,
  formatted to import cleanly into a new Letterboxd list

## Configuration

Poster images are looked up from [TMDB](https://www.themoviedb.org/), which
requires a free API key (Settings → API on your TMDB account). Without it,
the app works exactly the same — matches just come back with no `posterUrl`.

For local development, copy `.env.example` to `.env` and fill in
`TMDB_API_KEY`:

```bash
cp .env.example .env
```

`.env` is loaded automatically (via [spring-dotenv](https://github.com/paulschwarz/spring-dotenv))
and gitignored, so it's picked up every time regardless of which terminal
session you're in — no manual `export` needed. In production (Render), set
`TMDB_API_KEY` as a real environment variable in the dashboard instead;
`.env` files are a local-dev convenience only.

## Run

```bash
mvn spring-boot:run
```

This builds the Vue frontend into `src/main/resources/static` first, then
serves everything (API + UI) from `http://localhost:8080`.

## Develop

Backend only:

```bash
mvn spring-boot:run -Dskip.frontend.build=true
```

Frontend with hot reload (proxies `/api` to the backend on 8080):

```bash
cd frontend
npm install
npm run dev
```

## Test

```bash
mvn test
```

Runs both the backend suite (JUnit + Mockito) and the frontend suite
(Vitest + Vue Test Utils). Skip the frontend half with
`-Dskip.frontend.build=true`, or run it on its own:

```bash
cd frontend
npm test
```

## API

Interactive docs (Swagger UI) are served at `/swagger-ui.html` whenever the
app is running; the raw OpenAPI spec is at `/v3/api-docs`.

### `GET /api/intersect?user1={username}&user2={username}`

Returns `200` with a JSON array of matches, sorted alphabetically by title:

```json
[
  {
    "title": "The Outrun (2024)",
    "url": "https://letterboxd.com/film/the-outrun/",
    "year": 2024,
    "posterUrl": "https://image.tmdb.org/t/p/w342/abc123.jpg"
  }
]
```

`year` is parsed from the title (not the slug, which can carry a different
disambiguation year) and is `null` if it couldn't be determined. `posterUrl`
is `null` if `TMDB_API_KEY` isn't set or TMDB has no match.

Add `&random=true` to get a single random film from the overlap instead of
the full list — the response is still an array, just with 0 or 1 elements.
Only that one film gets a poster lookup. There's no exclude/no-repeat
parameter — every call (including "pick again") is an independent,
genuinely random draw from the full overlap, so it can occasionally repeat
the previous pick.

Returns `400` with `{ "error": "..." }` if either watchlist is private or
the username doesn't exist.

### `GET /api/watchlist?user={username}`

Single-user counterpart to `/api/intersect` — same response shape, same
`&random=true` behavior, but for one person's own watchlist instead of an
overlap between two:

```json
[
  {
    "title": "The Outrun (2024)",
    "url": "https://letterboxd.com/film/the-outrun/",
    "year": 2024,
    "posterUrl": "https://image.tmdb.org/t/p/w342/abc123.jpg"
  }
]
```

Returns `400` with `{ "error": "..." }` if the watchlist is private or the
username doesn't exist.

Both endpoints share the same response-building logic (`FilmResponseService`)
for the "full list vs. one random pick, with poster lookups" behavior.

### `GET /api/users/{username}/exists`

Checks only the first watchlist page, without walking pagination. Used by
the frontend to validate a username as it's typed, before enabling the
submit button.

```json
{ "exists": true, "watchlistPublic": true, "avatarUrl": "https://a.ltrbxd.com/resized/avatar/upload/..." }
```

- `exists: false` — the username doesn't exist on Letterboxd
- `exists: true, watchlistPublic: false` — the user exists, but their
  watchlist isn't public (or is empty)
- `exists: true, watchlistPublic: true` — ready to use

`avatarUrl` is `null` if the profile has no avatar in the page markup.
