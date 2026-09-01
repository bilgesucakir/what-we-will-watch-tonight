# what-we-will-watch-tonight

Spring Boot + Vue tool that scrapes public Letterboxd watchlists with Jsoup
and helps a group decide what to watch.

It intersects up to four people's watchlists and hands back **one random
film** — optionally only ones you can stream tonight — with its TMDB
poster, rating and runtime. The full overlap is one click away, and
exports as CSV ready to import into a new Letterboxd list.

Two modes, one per tab:

| Tab | What it picks from |
|---|---|
| **Just Me** | one person's own watchlist |
| **Us** | what 2–4 people's watchlists have in common |

Both default to a single random pick — for when you want an answer, not a
list to argue about.

## Demo

https://github.com/user-attachments/assets/86a14294-cb2b-41a1-8528-61a1ebd909a5


## Features

- **Random pick, solo or group** — the primary action in both tabs: picks
  one random film (from one watchlist, or from the overlap of 2–4) and
  shows it front and center with its poster, average Letterboxd rating and
  runtime. Only the picked film is enriched — its Letterboxd page is
  scraped for the rating, runtime and exact TMDB id
- **2–4 people in the "Us" tab** — start with two username fields, "**+ Add
  person**" for a third and fourth (each removable inline); each verified
  user's Letterboxd avatar appears above the form as they're added
- **"Pick something streamable"** — an optional filter: pick your
  country (auto-detected) and the streaming services you have, and the
  random pick is limited to films available on them. If nothing shared is,
  you still get a pick, flagged as not on your services. Availability from
  TMDB / JustWatch, remembered in your browser.
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
- **No duplicate people** — the same username in two fields is flagged
  inline ("already in the list"), never fetched a second time, and keeps
  the buttons disabled; the API rejects it too
- **Responsive** — a single layout that adapts from desktop down to phone
  widths
- **CSV export** — from the full-list view, download the results as CSV,
  formatted to import cleanly into a new Letterboxd list

## Configuration

Poster images and streaming availability are looked up from
[TMDB](https://www.themoviedb.org/) (the latter powered by JustWatch),
which requires a free API key (Settings → API on your TMDB account).
Without it, the app works exactly the same — matches just come back with no
`posterUrl`, and the streaming filter has no services to offer.

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

| Endpoint | Purpose |
|---|---|
| `GET /api/intersect?user=…&user=…` | Films on every one of 2–4 watchlists, or one random pick |
| `GET /api/watchlist?user=…` | One user's watchlist, or one random pick |
| `GET /api/streaming-providers?region=…` | Streaming services in a region (builds the filter chips) |
| `GET /api/underwatched-pick` | One random film from a curated underseen list |
| `GET /api/users/{username}/exists` | Username + public-watchlist check, for live validation |

All film-returning endpoints share one response builder (`FilmResponseService`),
and every `400` is shaped by a `@RestControllerAdvice` (`ApiExceptionHandler`)
so failures always look the same.

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
    "rating": null,
    "length": null,
    "posterUrl": "https://image.tmdb.org/t/p/w342/abc123.jpg",
    "providers": []
  }
]
```

- `year` is parsed from the title (not the slug, which can carry a
  different disambiguation year); `null` if it couldn't be determined.
- `posterUrl` is `null` if `TMDB_API_KEY` isn't set or nothing matches. In
  the full list it's a TMDB title search (movies **and** TV — Letterboxd
  lists some mini-series as films) ranked by exact title (English or
  original-language), then `year`, then popularity; if that's still
  ambiguous the film's Letterboxd page is scraped for the exact TMDB id,
  same as a random pick.
- `rating` (average Letterboxd rating, 0–5) and `length` (runtime in
  minutes) are **only filled in for a single random pick** — see below.
  In the full list they're always `null`.
- `providers` is only populated for a random pick made with the streaming
  filter (see below); otherwise it's an empty array.

Add `&random=true` to get a single random film from the overlap instead of
the full list — the response is still an array, just with 0 or 1 elements.
That one film gets its Letterboxd page scraped for `rating`, `length` and
the exact TMDB id, and the poster is fetched from that id rather than a
title guess. There's no exclude/no-repeat parameter — every call
(including "pick again") is an independent random draw, so it can
occasionally repeat the previous pick.

```json
[
  {
    "title": "The Outrun (2024)",
    "url": "https://letterboxd.com/film/the-outrun/",
    "year": 2024,
    "rating": 3.6,
    "length": 118,
    "posterUrl": "https://image.tmdb.org/t/p/w342/abc123.jpg",
    "providers": [
      { "id": 8, "name": "Netflix", "logoUrl": "https://image.tmdb.org/t/p/w45/abc.jpg" }
    ]
  }
]
```

**Streaming filter.** Add `&provider={tmdbId}` (repeatable) **and**
`&region={ISO-3166-1}` alongside `&random=true` to restrict the pick to
films streamable on those services (subscription / free / ad-supported —
not rent or buy), per TMDB's watch-provider data for that region. Both
params are required — there's no default region. The response is a random
film that's on one of the given services, with `providers` filled in; if
none of the shared films are, it's a random pick with `providers` you can
check against your selection. The params are ignored without
`&random=true`. Get provider ids for a region from
`/api/streaming-providers`.

Returns `400` with `{ "error": "..." }`, one distinct message per problem:

- not between 2 and 4 usernames
- a username is blank
- two of the usernames are the same (case-insensitive)
- a user doesn't exist on Letterboxd
- a watchlist is private or empty

### `GET /api/watchlist?user={username}`

Single-user counterpart to `/api/intersect` — same response shape (with the
same `rating` / `length` rules), same `&random=true` behavior (including the
`&provider=` / `&region=` streaming filter), but for one person's own
watchlist. Returns `200` with a JSON array of that user's films, sorted
alphabetically by title:

```json
[
  {
    "title": "The Outrun (2024)",
    "url": "https://letterboxd.com/film/the-outrun/",
    "year": 2024,
    "rating": null,
    "length": null,
    "posterUrl": "https://image.tmdb.org/t/p/w342/abc123.jpg",
    "providers": []
  }
]
```

With `&random=true` the array holds 0 or 1 elements, and that one film has
`rating`, `length` and (when the streaming filter is on) `providers`
filled in — exactly as for `/api/intersect`.

Returns `400` with `{ "error": "..." }` — a distinct message for a blank
username, a user that doesn't exist, and a private/empty watchlist.

### `GET /api/streaming-providers?region={ISO-3166-1}`

Returns `200` with the streaming services TMDB (via JustWatch) lists for
movies in that region, most mainstream first — used to build the streaming
filter's chips. `region` is required (`400` without it). Empty array if
`TMDB_API_KEY` isn't set.

```json
[
  { "id": 8, "name": "Netflix", "logoUrl": "https://image.tmdb.org/t/p/w45/abc.jpg" },
  { "id": 337, "name": "Disney Plus", "logoUrl": "https://image.tmdb.org/t/p/w45/def.jpg" }
]
```

### `GET /api/underwatched-pick`

Returns `200` with a single film (same object shape as one array element
above — poster, `rating`, `length` all filled in) drawn at random from the
curated underwatched list, or `204` if that list is empty. The frontend
calls this when `/api/intersect` comes back with nothing in common.

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
