# what-we-will-watch-tonight

<p align="center">
  <img src="docs/sofa.png" alt="A red two-seat sofa next to a striped bucket of popcorn">
</p>

Can't decide what to watch? Enter your Letterboxd username, or up to four
people, and get **one film** from your watchlists. With a group, the pick
comes only from films that are on everyone's watchlist.

**Try it:** [what-we-will-watch-tonight.onrender.com](https://what-we-will-watch-tonight.onrender.com)

Two modes, one per tab:

| Tab | What it picks from |
|---|---|
| **Us** | films on every person's watchlist (2 to 4 people) |
| **Just Me** | your own watchlist |

Built with Spring Boot and Vue. Watchlists are read from public Letterboxd
pages; posters and streaming availability come from TMDB.

## Demo
https://github.com/user-attachments/assets/9254ff44-2fe0-4f6c-80b1-3995a195b1de

## Features

### Pick a film

- **One random pick.** The main button picks a single film and shows its
  poster, Letterboxd rating and runtime, with a link to the film on
  Letterboxd. Not feeling it? Press it again for another.
- **Only things you can stream.** Tick "Pick something streamable", choose
  your country (detected automatically) and the services you have. The pick
  then comes only from films on those services, and the card shows where to
  watch it. If nothing you share is on them, you still get a pick, marked as
  not on your services. Your choices are remembered in your browser.
- **Nothing in common?** If the watchlists don't overlap at all, you get a
  surprise instead: a random film from Letterboxd's
  [Top 100 Underseen Films](https://letterboxd.com/official/list/top-100-underseen-films/).

### Browse everything

- **The whole list.** "Return all films" shows every match as a poster grid,
  A to Z, with the total at the top. It shows 24 films at a time; "Show more"
  loads the next 24. Each poster links to the film on Letterboxd.
- **CSV export.** "Download CSV" saves the whole list, not just what's on
  screen, in a format Letterboxd can import as a new list.

### Who's watching

- **2 to 4 people.** The Us tab starts with two username fields. "+ Add
  person" adds a third and fourth, and each extra person can be removed.
- **Checked as you type.** Each username is checked against Letterboxd: does
  the user exist, and is their watchlist public? If not, a message appears
  under the field, and the buttons stay off until everyone is ready. The same
  username entered twice is caught too.
- **Everyone on the sofa.** As each username checks out, that person's
  Letterboxd avatar takes a seat on the sofa at the top of the page.

### Works for everyone

- Works on phones and desktops.
- Usable with a keyboard and a screen reader.
- Animations calm down if your system asks for reduced motion.

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

The frontend talks to a small JSON API. Every endpoint, parameter, example
response and error is documented in Swagger UI:

- **Live:** [what-we-will-watch-tonight.onrender.com/swagger-ui.html](https://what-we-will-watch-tonight.onrender.com/swagger-ui.html)
- **Locally:** `http://localhost:8080/swagger-ui.html` while the app is
  running (the raw OpenAPI spec is at `/v3/api-docs`)

| Endpoint | What it does |
|---|---|
| `GET /api/intersect` | Films on every one of 2 to 4 watchlists, or one random pick |
| `GET /api/watchlist` | One person's watchlist, or one random pick |
| `POST /api/posters` | Posters for one page of the full list |
| `GET /api/streaming-providers` | Streaming services in a country, for the filter |
| `GET /api/underwatched-pick` | One random film from the underseen list |
| `GET /api/users/{username}/exists` | Whether a user exists and their watchlist is public |

Problems with a request come back as `400` with a plain message, for
example `{ "error": "Enter between 2 and 4 usernames." }`.
