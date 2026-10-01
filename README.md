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
https://github.com/user-attachments/assets/9254ff44-2fe0-4f6c-80b1-3995a195b1de





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
  A bar above the grid shows the total ("33 films in common"). The grid shows
  24 films at a time; "**Show more**" loads the next 24. The list itself
  arrives in one response; posters are looked up only for the films on
  screen, a page at a time.

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

  "**Download CSV**", in the bar above the full-list grid, downloads the whole
  list (not just the films on screen) as CSV, formatted to import cleanly
  into a new Letterboxd list.

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
**`target/site/jacoco/index.html`** (backend, ~96% line / ~88% branch).

For the frontend:

```bash
cd frontend
npm run test:coverage
```

writes a v8 report to **`frontend/coverage/index.html`** (~98% line / ~92% branch).

## API

Interactive docs (Swagger UI) are served at `/swagger-ui.html` whenever the
app is running; the raw OpenAPI spec is at `/v3/api-docs`.

| Endpoint | Purpose |
|---|---|
| `GET /api/intersect?user=…&user=…` | Films on every one of 2–4 watchlists, or one random pick |
| `GET /api/watchlist?user=…` | One user's watchlist, or one random pick |
| `POST /api/posters` | Posters for one page of a full list |
| `GET /api/streaming-providers?region=…` | Streaming services in a region (builds the filter chips) |
| `GET /api/underwatched-pick` | One random film from a curated underseen list |
| `GET /api/users/{username}/exists` | Username + public-watchlist check, for live validation |

All film-returning endpoints share one response builder (`FilmResponseService`).

### Errors

Problems with a request return `400` and one shape, built by a
`@RestControllerAdvice` (`ApiExceptionHandler`):

```json
{ "error": "Enter between 2 and 4 usernames." }
```

Each endpoint below lists its exact messages. A few requests fail before
they reach the app's own checks and get Spring's default error body instead:

| Status | When | Example |
|---|---|---|
| `400` | A parameter has the wrong type (`random=abc`, `provider=abc`) | `?user=a&user=b&random=abc` |
| `405` | Wrong HTTP method | `GET /api/posters` |
| `415` | A body that isn't sent as `application/json` | `POST /api/posters` with `Content-Type: text/plain` |

```json
{ "timestamp": "2026-10-01T09:11:06.264+00:00", "status": 400, "error": "Bad Request", "path": "/api/intersect" }
```

### `GET /api/intersect?user={username}&user={username}[&user=…]`

Films on every one of 2 to 4 watchlists. Two modes: the full overlap
(default), or one random pick (`&random=true`).

| Parameter | Required | Notes |
|---|---|---|
| `user` | yes | Letterboxd username, repeated 2 to 4 times. |
| `random` | no | `true` for one random pick. Default `false`. |
| `provider` | no | TMDB provider id, repeatable. Random pick only; needs `region`. See [Streaming filter](#streaming-filter-random-pick-only). |
| `region` | no | ISO-3166-1 country code. Random pick only; needs `provider`. |

Both modes return `200` and a JSON array of the same object:

| Field | Notes |
|---|---|
| `title` | Letterboxd title, with year. |
| `url` | Letterboxd film page. |
| `year` | Parsed from the title, not the slug. `null` if it can't be determined. |
| `rating` | Average Letterboxd rating, 0–5. Random pick only; `null` in the full list. |
| `length` | Runtime in minutes. Random pick only; `null` in the full list. |
| `posterUrl` | TMDB poster. Random pick only; always `null` in the full list (fetch those a page at a time from [`POST /api/posters`](#post-apiposters)). `null` too if `TMDB_API_KEY` is unset or nothing matches. |
| `providers` | Streaming services carrying the film. `[]` unless the streaming filter is on. |

#### Full list (default)

```bash
curl "http://localhost:8080/api/intersect?user=karsten&user=schaffrillas"
```

```json
[
  {
    "title": "Amélie (2001)",
    "url": "https://letterboxd.com/film/amelie/",
    "year": 2001,
    "rating": null,
    "length": null,
    "posterUrl": null,
    "providers": []
  },
  {
    "title": "Barb & Star Go to Vista Del Mar (2021)",
    "url": "https://letterboxd.com/film/barb-star-go-to-vista-del-mar/",
    "year": 2021,
    "rating": null,
    "length": null,
    "posterUrl": null,
    "providers": []
  }
]
```

- Every film on all 2–4 watchlists, sorted alphabetically by title
  (case-insensitive). `[]` if nothing overlaps.
- No lookups beyond the watchlists themselves, so the time is all in
  scraping the watchlists.
- The frontend shows 24 films at a time and asks
  [`POST /api/posters`](#post-apiposters) for each page's posters. The list
  lives in the browser, so the server keeps no state between requests.

#### Random pick (`&random=true`)

```bash
curl "http://localhost:8080/api/intersect?user=karsten&user=schaffrillas&random=true"
```

```json
[
  {
    "title": "The Adventures of Prince Achmed (1926)",
    "url": "https://letterboxd.com/film/the-adventures-of-prince-achmed/",
    "year": 1926,
    "rating": 3.99,
    "length": 66,
    "posterUrl": "https://image.tmdb.org/t/p/w342/c3OKMlt9QxpPeSfpyViqYkYFvUz.jpg",
    "providers": []
  }
]
```

- One random film from the overlap. The array holds 1 element, or 0 if
  nothing overlaps.
- `rating` and `length` are scraped from that film's Letterboxd page.
- The same page is scraped for the film's TMDB entry; `posterUrl` and any
  streaming lookup use that entry, not a title guess.
- Every call is an independent draw. There's no no-repeat parameter, so "pick
  again" can repeat the last result.

#### Streaming filter (random pick only)

Add `&provider={tmdbId}` (repeatable) and `&region={ISO-3166-1}`, alongside
`&random=true`:

```bash
curl "http://localhost:8080/api/intersect?user=karsten&user=schaffrillas&random=true&provider=8&provider=9&region=US"
```

```json
[
  {
    "title": "The General (1926)",
    "url": "https://letterboxd.com/film/the-general/",
    "year": 1926,
    "rating": 4.18,
    "length": 79,
    "posterUrl": "https://image.tmdb.org/t/p/w342/4NmV1Wei4LxT2lpjViCAScgCZLq.jpg",
    "providers": [
      { "id": 9, "name": "Amazon Prime Video", "logoUrl": "https://image.tmdb.org/t/p/w45/gMZdpavHmxFNnLpMHwVxfqeux2g.png" },
      { "id": 34, "name": "MGM Plus", "logoUrl": "https://image.tmdb.org/t/p/w45/q63Uzpu7JAs566vA2G23Lk7LcID.png" }
    ]
  }
]
```

- The filter needs both `provider` and `region`. If either is missing, or
  `random` isn't `true`, it's silently ignored and you get an unfiltered
  result.
- The pick is limited to films streamable on those services: subscription,
  free or ad-supported. Not rent or buy.
- Availability is TMDB's watch-provider data for that region.
- `providers` lists **every** service carrying the film in that region, not
  just the ones you asked for.
- The whole overlap is checked before giving up. If nothing is streamable on
  your services, you still get a random pick, and `providers` shows where it
  *is* available (possibly `[]`).
- Get provider ids from [`/api/streaming-providers`](#get-apistreaming-providersregioniso-3166-1).

#### Errors

`400` with `{ "error": "..." }`. Checked in this order:

| Problem | Message |
|---|---|
| `user` is missing | `Missing required parameter: user.` |
| Not 2 to 4 usernames | `Enter between 2 and 4 usernames.` |
| A username is blank | `Fill in every username.` |
| The same username twice (case-insensitive) | `Enter a different username in each field.` |
| A user doesn't exist on Letterboxd | `There's no Letterboxd user named 'alice'.` (several: `No Letterboxd users named: alice, bob.`) |
| A watchlist is private or empty | `The watchlist for 'alice' is private or empty.` (several: `These watchlists are private or empty: alice, bob.`) |

If Letterboxd can't be reached, that watchlist is reported as private or
empty. A wrongly typed `random` or `provider` gets Spring's default `400`
body (see [Errors](#errors)).

### `GET /api/watchlist?user={username}`

Single-user counterpart to `/api/intersect`, for one person's own watchlist.

| Parameter | Required | Notes |
|---|---|---|
| `user` | yes | One Letterboxd username. |
| `random` | no | `true` for one random pick. Default `false`. |
| `provider` | no | Same as `/api/intersect`. Random pick only; needs `region`. |
| `region` | no | Same as `/api/intersect`. Random pick only; needs `provider`. |

Same response object, field rules, modes and streaming filter as
`/api/intersect`. Returns `200` and a JSON array.

Full list:

```bash
curl "http://localhost:8080/api/watchlist?user=schaffrillas"
```

```json
[
  {
    "title": "Amélie (2001)",
    "url": "https://letterboxd.com/film/amelie/",
    "year": 2001,
    "rating": null,
    "length": null,
    "posterUrl": null,
    "providers": []
  }
]
```

Random pick:

```bash
curl "http://localhost:8080/api/watchlist?user=schaffrillas&random=true"
```

```json
[
  {
    "title": "Mommy (2014)",
    "url": "https://letterboxd.com/film/mommy-2014/",
    "year": 2014,
    "rating": 4.32,
    "length": 138,
    "posterUrl": "https://image.tmdb.org/t/p/w342/uPDP0cHGOpkr47rdCdHWo4CyiPj.jpg",
    "providers": []
  }
]
```

#### Errors

`400` with `{ "error": "..." }`:

| Problem | Message |
|---|---|
| `user` is missing | `Missing required parameter: user.` |
| The username is blank | `Fill in every username.` |
| The user doesn't exist on Letterboxd | `There's no Letterboxd user named 'alice'.` |
| The watchlist is private or empty | `The watchlist for 'alice' is private or empty.` |

If Letterboxd can't be reached, the watchlist is reported as private or
empty. A wrongly typed `random` or `provider` gets Spring's default `400`
body (see [Errors](#errors)).

### `POST /api/posters`

Posters for one page of a full list. Send `1` to `48` films as
`application/json`, exactly as the full list returned them. Only `url`,
`title` and `year` are read; other fields are ignored.

```bash
curl -X POST "http://localhost:8080/api/posters" \
  -H "Content-Type: application/json" \
  -d '[
        { "url": "https://letterboxd.com/film/amelie/", "title": "Amélie (2001)", "year": 2001 },
        { "url": "https://letterboxd.com/film/barb-star-go-to-vista-del-mar/", "title": "Barb & Star Go to Vista Del Mar (2021)", "year": 2021 }
      ]'
```

| Body field | Required | Notes |
|---|---|---|
| `url` | yes | A Letterboxd film URL, `https://letterboxd.com/film/{slug}/`. |
| `title` | yes | The Letterboxd title, used for the TMDB search. |
| `year` | no | Helps pick the right match. May be `null`. |

Returns `200` and one entry per film, in the same order:

```json
[
  { "url": "https://letterboxd.com/film/amelie/", "posterUrl": "https://image.tmdb.org/t/p/w342/nSxDa3M9aMvGVLoItzWTepQ5h5d.jpg" },
  { "url": "https://letterboxd.com/film/barb-star-go-to-vista-del-mar/", "posterUrl": "https://image.tmdb.org/t/p/w342/m0kQFuMSe6ImokuyG9xfRPtWLQ4.jpg" }
]
```

How each poster is found:

- A TMDB title search over movies **and** TV (Letterboxd lists some
  mini-series as films), ranked by exact title (English or
  original-language), then `year`, then popularity.
- If that search is still ambiguous, the Letterboxd page is scraped for the
  exact TMDB entry: its id, and whether it's a film or a series.
- `posterUrl` is `null` if `TMDB_API_KEY` is unset or nothing matches.

#### Errors

| Status | Problem | Message |
|---|---|---|
| `400` | Empty array, or more than 48 films | `Ask for between 1 and 48 posters at a time.` |
| `400` | A film's `url` isn't a Letterboxd film URL, it has no `title`, or an entry is `null` | `Not a Letterboxd film: https://example.com/film/anora/.` |
| `400` | The body isn't valid JSON | `The request body isn't valid JSON.` |
| `405` | Any method other than `POST` | Spring's default body (see [Errors](#errors)) |
| `415` | `Content-Type` isn't `application/json` | Spring's default body (see [Errors](#errors)) |

### `GET /api/streaming-providers?region={ISO-3166-1}`

Every subscription, free and ad-supported service TMDB (via JustWatch) lists
for movies in a region, most mainstream first. Builds the streaming filter's
chips.

| Parameter | Required | Notes |
|---|---|---|
| `region` | yes | ISO-3166-1 country code, any case (`US`, `tr`). |

```bash
curl "http://localhost:8080/api/streaming-providers?region=US"
```

```json
[
  { "id": 15, "name": "Hulu", "logoUrl": "https://image.tmdb.org/t/p/w45/44uAnmSqvA4yBOdbPWN8YgQHjWm.png" },
  { "id": 8, "name": "Netflix", "logoUrl": "https://image.tmdb.org/t/p/w45/rK1KljqmbvO9HQa1PBFLILWah72.png" }
]
```

Returns `200` with `[]` if `TMDB_API_KEY` is unset, TMDB can't be reached,
or the region is unknown (e.g. `ZZ`).

#### Errors

| Status | Problem | Message |
|---|---|---|
| `400` | `region` is missing | `Missing required parameter: region.` |

### `GET /api/underwatched-pick`

One random film from the curated underwatched list
(`src/main/resources/underwatched-films.json`). The frontend calls this when
`/api/intersect` finds nothing in common.

```bash
curl "http://localhost:8080/api/underwatched-pick"
```

Returns `200` and a single film (not an array), in the same shape as a
random pick: `rating`, `length` and `posterUrl` filled in, `providers`
always `[]`.

```json
{
  "title": "Repentance",
  "url": "https://letterboxd.com/film/repentance/",
  "year": 1984,
  "rating": 4.15,
  "length": 153,
  "posterUrl": "https://image.tmdb.org/t/p/w342/9kZqQ2tlwfts6ch2s4ibKhjQgzW.jpg",
  "providers": []
}
```

#### Errors

| Status | Problem | Body |
|---|---|---|
| `204` | The curated list is empty | none |

### `GET /api/users/{username}/exists`

Checks whether a username exists and whether its watchlist is public. It
fetches only the first page of the user's Letterboxd watchlist, not the
whole list. The frontend uses it to validate each username as it's typed,
before enabling the buttons.

```bash
curl "http://localhost:8080/api/users/karsten/exists"
```

Always `200` for a non-blank username; the answer is in the body.

Ready to use:

```json
{ "exists": true, "watchlistPublic": true, "avatarUrl": "https://a.ltrbxd.com/resized/avatar/upload/7/3/5/3/7/2/shard/avtr-0-220-0-220-crop.jpg?v=325c81cf73" }
```

Exists, but the watchlist isn't public (or is empty):

```json
{ "exists": true, "watchlistPublic": false, "avatarUrl": "https://a.ltrbxd.com/resized/avatar/twitter/3/2/1/9/2/shard/http___pbs.twimg.com_profile_images_1015061849321164801_S527QFnk-0-48-0-48-crop.jpg?v=e236c36c50" }
```

No such user:

```json
{ "exists": false, "watchlistPublic": false, "avatarUrl": null }
```

- `avatarUrl` is `null` if there's no user or the page has no avatar.
- An avatar uploaded to Letterboxd (`…/avtr-0-48-0-48-crop.jpg`) is rewritten
  to ask for a 220px crop instead of the page's tiny one. Other avatars, such
  as ones imported from Twitter (the second example), are returned as-is.
- If Letterboxd can't be reached, the check reports `exists: false`.

#### Errors

| Status | Problem | Message |
|---|---|---|
| `400` | The username is blank (e.g. `/api/users/%20/exists`) | `Fill in every username.` |
