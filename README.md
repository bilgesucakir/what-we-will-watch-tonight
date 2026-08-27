# what-we-will-watch-tonight

Spring Boot and Vue-based watchlist tool for Jsoup-driven Letterboxd
scraping, cross-user intersection matching, and CSV list export.

Scrapes public Letterboxd watchlist pages with Jsoup and helps you pick
something to watch, in two modes (two tabs in the UI):

- **Just Me** — a random pick from one person's own watchlist
- **Us** — a random pick from what a group's watchlists have in common
  (2 to 4 people), or the full overlap as a browsable list

Both modes hand back a single random pick by default — for when you just
want an answer, not a list to argue about — with the full film list still
one click away, plus a CSV export ready to import into a new Letterboxd
list.

## Features

- **Random pick, solo or group** — the primary action in both tabs: picks
  one random film (from one watchlist, or from the overlap of 2–4) and
  shows it front and center, with a poster from TMDB where one's found.
  Only the picked film gets a poster lookup
- **2–4 people in the "Us" tab** — start with two username fields, "**+ Add
  person**" for a third and fourth (each removable inline); the sofa in the
  background grows with the group and each verified user's avatar takes a
  cushion
- **"Return all films"** — a smaller secondary action in both tabs for
  browsing the full list as a poster grid, sorted alphabetically, each
  linking to its Letterboxd page
- **Nothing in common?** — if a group's watchlists don't overlap at all,
  the app hands back a random pick from a curated list of underwatched
  films instead (seeded from Letterboxd's
  [Top 100 Underseen Films](https://letterboxd.com/official/list/top-100-underseen-films/),
  kept static in `src/main/resources/underwatched-films.json`)
- **Live username validation** — as you type, checks whether each username
  exists on Letterboxd and whether its watchlist is public, keeping the
  buttons disabled until ready
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

### Coverage

`mvn test` also writes a JaCoCo report to
**`target/site/jacoco/index.html`** (backend, ~97% line / ~97% branch).

For the frontend:

```bash
cd frontend
npm run test:coverage
```

writes a v8 report to **`frontend/coverage/index.html`** (100% line / ~95% branch).

## API

Interactive docs (Swagger UI) are served at `/swagger-ui.html` whenever the
app is running; the raw OpenAPI spec is at `/v3/api-docs`.

### `GET /api/intersect?user={username}&user={username}[&user=…]`

Pass the `user` parameter **2 to 4 times**. Returns `200` with a JSON array
of the films on *every* one of those watchlists, sorted alphabetically by
title:

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

Returns `400` with `{ "error": "..." }`, one distinct message per problem:

- not between 2 and 4 usernames
- a username is blank
- a user doesn't exist on Letterboxd
- a watchlist is private or empty

### `GET /api/watchlist?user={username}`

Single-user counterpart to `/api/intersect` — same response shape, same
`&random=true` behavior, but for one person's own watchlist:

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

Returns `400` with `{ "error": "..." }` — a distinct message for a blank
username, a user that doesn't exist, and a private/empty watchlist.

### `GET /api/underwatched-pick`

Returns `200` with a single film (same shape as one array element above,
with its poster) drawn at random from the curated underwatched list, or
`204` if that list is empty. The frontend calls this when `/api/intersect`
comes back with nothing in common.

All film-returning endpoints share the same response-building logic
(`FilmResponseService`) for the "full list vs. one random pick, with poster
lookups" behavior. Every `400` is shaped by a `@RestControllerAdvice`
(`ApiExceptionHandler`) so failures always look the same.

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

`avatarUrl` is `null` if the profile has no avatar in the page markup, and is
rewritten to request a larger crop than the tiny one in the page source.
