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

- **Random pick, solo or group**

  The primary action in both tabs. Picks one random film — from one watchlist,
  or from the overlap of 2–4 — and shows it front and center with its poster,
  average Letterboxd rating and runtime. Only the picked film is enriched: its
  Letterboxd page is scraped for the rating, runtime and exact TMDB id.

- **2–4 people in the "Us" tab**

  Start with two username fields. "**+ Add person**" adds a third and fourth,
  each removable inline. Each verified user's Letterboxd avatar appears above
  the form as they're added.

- **"Pick something streamable"**

  An optional filter. Pick your country (auto-detected) and the streaming
  services you have; the random pick is then limited to films on them. If
  nothing shared is, you still get a pick, flagged as not on your services.
  Availability comes from TMDB / JustWatch and is remembered in your browser.

- **"Return all films"**

  A smaller secondary action in both tabs. Browses the full list as a poster
  grid, sorted alphabetically, each poster linking to its Letterboxd page.

- **Nothing in common?**

  If a group's watchlists don't overlap at all, the app hands back a random
  pick from a curated list of underwatched films instead. Seeded from
  Letterboxd's
  [Top 100 Underseen Films](https://letterboxd.com/official/list/top-100-underseen-films/),
  kept static in `src/main/resources/underwatched-films.json`.

- **Live username validation**

  As you type, each username is checked against Letterboxd — does it exist, is
  its watchlist public. The buttons stay disabled until every field is ready.

- **No duplicate people**

  The same username in two fields is flagged inline ("already in the list"),
  never fetched twice, and keeps the buttons disabled. The API rejects it too.

- **Responsive**

  A single layout that adapts from desktop down to phone widths.

- **CSV export**

  From the full-list view, download the results as CSV — formatted to import
  cleanly into a new Letterboxd list.

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

Pass `user` **2 to 4 times**. Two modes: the full overlap (default), or one
random pick (`&random=true`). Both return `200` and a JSON array, sorted
alphabetically by title, of the same object:

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

| Field | Notes |
|---|---|
| `title` | Letterboxd title, with year. |
| `url` | Letterboxd film page. |
| `year` | Parsed from the title, not the slug. `null` if it can't be determined. |
| `posterUrl` | TMDB poster. `null` if `TMDB_API_KEY` is unset or nothing matches. Resolved differently per mode — see below. |
| `rating` | Average Letterboxd rating, 0–5. |
| `length` | Runtime in minutes. |
| `providers` | Streaming services carrying the film. |

#### Full list (default)

- Every film on all 2–4 watchlists.
- `rating` and `length` are always `null`.
- `providers` is always `[]`.
- `posterUrl` comes from a TMDB title search over movies **and** TV
  (Letterboxd lists some mini-series as films), ranked by exact title
  (English or original-language), then `year`, then popularity.
- If that search is still ambiguous, the Letterboxd page is scraped for the
  exact TMDB entry — its id, and whether it's a film or a series.

#### Random pick (`&random=true`)

- One random film from the overlap. The array holds 0 or 1 elements.
- `rating` and `length` are filled in, scraped from that film's Letterboxd
  page.
- The same page is scraped for the film's TMDB entry; `posterUrl` and any
  streaming lookup use that entry, not a title guess.
- Every call is an independent draw. There's no no-repeat parameter, so "pick
  again" can repeat the last result.

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

#### Streaming filter (random pick only)

Add `&provider={tmdbId}` (repeatable) and `&region={ISO-3166-1}`, alongside
`&random=true`.

- Both params are required. There's no default region.
- The pick is limited to films streamable on those services — subscription,
  free or ad-supported. Not rent or buy.
- Availability is TMDB's watch-provider data for that region.
- Normal case: a random film on one of your services, `providers` filled in.
- If nothing shared is streamable: you still get a random pick, and
  `providers` lists where it *is* available, to check against your selection.
- Ignored without `&random=true`.
- Get provider ids from `/api/streaming-providers`.

#### Errors

`400` with `{ "error": "..." }`, one distinct message per problem:

- not between 2 and 4 usernames
- a username is blank
- two usernames are the same (case-insensitive)
- a user doesn't exist on Letterboxd
- a watchlist is private or empty

### `GET /api/watchlist?user={username}`

Single-user counterpart to `/api/intersect`, for one person's own watchlist.

- Same response object and field rules.
- Same two modes: full list (default) or random pick (`&random=true`).
- Same streaming filter (`&provider=` / `&region=`), random pick only.
- Returns `200` and a JSON array sorted alphabetically by title.

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

`400` with `{ "error": "..." }`, one distinct message per problem:

- a username is blank
- a user doesn't exist on Letterboxd
- a watchlist is private or empty

### `GET /api/streaming-providers?region={ISO-3166-1}`

Returns `200` and the streaming services TMDB (via JustWatch) lists for movies
in that region, most mainstream first. Builds the streaming filter's chips.
`region` is required — `400` without it. Empty array if `TMDB_API_KEY` is
unset.

```json
[
  { "id": 8, "name": "Netflix", "logoUrl": "https://image.tmdb.org/t/p/w45/abc.jpg" },
  { "id": 337, "name": "Disney Plus", "logoUrl": "https://image.tmdb.org/t/p/w45/def.jpg" }
]
```

### `GET /api/underwatched-pick`

Returns `200` and a single film — same object shape as one array element
above, with `posterUrl`, `rating` and `length` filled in. Drawn at random from
the curated underwatched list. `204` if that list is empty. The frontend calls
this when `/api/intersect` finds nothing in common.

### `GET /api/users/{username}/exists`

Checks only the first watchlist page — no pagination. The frontend uses it to
validate a username as it's typed, before enabling the submit button.

```json
{ "exists": true, "watchlistPublic": true, "avatarUrl": "https://a.ltrbxd.com/resized/avatar/upload/..." }
```

States:

- `exists: false` — the username doesn't exist on Letterboxd.
- `exists: true, watchlistPublic: false` — the user exists, but the watchlist
  isn't public (or is empty).
- `exists: true, watchlistPublic: true` — ready to use.

`avatarUrl` is `null` if the profile markup has no avatar. When present, it's
rewritten to request a larger crop than the page's tiny one.
