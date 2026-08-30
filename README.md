# what-we-will-watch-tonight

Spring Boot and Vue tool that scrapes public Letterboxd watchlists with
Jsoup, intersects them across up to four people, and hands back one random
film to watch — optionally only ones you can stream tonight — with TMDB
posters, ratings and runtimes, plus a CSV export of the full overlap.

Helps you pick something to watch, in two modes (two tabs in the UI):

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
  shows it front and center with its poster, average Letterboxd rating and
  runtime. Only the picked film is enriched — its Letterboxd page is
  scraped for the rating, runtime and exact TMDB id (so the poster is the
  right one, not a title guess)
- **2–4 people in the "Us" tab** — start with two username fields, "**+ Add
  person**" for a third and fourth (each removable inline); each verified
  user's Letterboxd avatar appears above the form as they're added
- **"Only pick something we can stream"** — an optional filter under the
  buttons: your country is detected from the browser (timezone, falling
  back to locale), then you tick the streaming services you have and the
  random pick is re-rolled until it lands on a film available on one of
  them (the pick card then shows where it's streaming). Availability comes
  from TMDB's watch-provider data, powered by JustWatch. Region + services
  are remembered in your browser. It only affects the random pick —
  asking for the full list switches the filter off and clears it, so
  there's never any doubt about whether a list is filtered
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
  widths, tested against mobile Safari
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
- `posterUrl` is `null` if `TMDB_API_KEY` isn't set or nothing matches.
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
films streamable on those services. Both are needed — there's no default
region; the frontend detects it from the browser (timezone, then locale).
The backend walks a shuffled overlap, checking each candidate's TMDB
watch-provider data (subscription / free / ad-supported — not rent or buy)
for the region, and returns the first film on one of the given services,
with its `providers` filled in. If nothing in the overlap is streamable on
them it still returns a film (so the UI can say "not on your services").
The params are ignored without `&random=true`, or if either is missing.
Get the list of provider ids for a region from `/api/streaming-providers`.

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
