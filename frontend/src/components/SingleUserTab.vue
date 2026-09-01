<script setup>
import { ref, computed } from 'vue'
import { useUsernameCheck, usernameFieldError } from '../composables/useUsernameCheck'
import { useStreamingFilter, streamingNote } from '../composables/useStreamingFilter'
import { downloadFilmsAsCsv } from '../utils/csv'
import { pickMeta } from '../utils/format'
import StreamingFilter from './StreamingFilter.vue'
import SofaStage from './SofaStage.vue'

const username = ref('')
const loading = ref(false)
const error = ref('')
const matches = ref(null)
const pendingAction = ref(null)
const lastSearchWasRandom = ref(false)
const pickStreamingNote = ref(null)
// Set when "return all films" cleared an active streaming filter.
const clearedFilterForList = ref(false)

// "Only pick something we can stream" -- shared with the Us tab, remembered
// in localStorage. Only affects the random pick.
const streaming = useStreamingFilter()

// The username the current `matches` results actually came from, captured
// at search time so the CSV filename stays correct even if the input is
// edited afterward without re-searching.
const searchedUsername = ref('')

const { exists, watchlistPublic, avatarUrl } = useUsernameCheck(username)

const canSubmit = computed(() => username.value.trim() !== '' && watchlistPublic.value === true && !loading.value)
const fieldError = computed(() => usernameFieldError(exists.value, watchlistPublic.value))

async function search(random) {
  error.value = ''
  matches.value = null
  pickStreamingNote.value = null
  clearedFilterForList.value = false
  lastSearchWasRandom.value = random

  const trimmed = username.value.trim()
  if (!trimmed) return

  // The full list is never streaming-filtered -- asking for it switches the
  // filter off and drops the selection.
  if (!random && streaming.enabled.value) {
    streaming.enabled.value = false
    streaming.clear()
    clearedFilterForList.value = true
  }

  loading.value = true
  pendingAction.value = random ? 'tonight' : 'all'

  const params = new URLSearchParams({ user: trimmed })
  if (random) {
    params.set('random', 'true')
    streaming.pickParams().forEach(([key, value]) => params.append(key, value))
  }

  try {
    const response = await fetch(`/api/watchlist?${params}`)
    const body = await response.json()

    if (!response.ok) {
      error.value = body.error || 'Something went wrong.'
      return
    }

    matches.value = body
    searchedUsername.value = trimmed

    if (random && body.length > 0) {
      pickStreamingNote.value = streamingNote(body[0], streaming)
    }
  } catch (e) {
    error.value = 'Could not reach the server. Please try again.'
  } finally {
    loading.value = false
    pendingAction.value = null
  }
}

function findAllFilms() {
  return search(false)
}

function findTonightsPick() {
  return search(true)
}

function downloadCsv() {
  downloadFilmsAsCsv(matches.value, `${searchedUsername.value}_watchlist.csv`)
}
</script>

<template>
  <SofaStage :count="1" :avatars="[avatarUrl]" />

  <h1>What I'll Watch Tonight</h1>
  <p class="subtitle">Enter your Letterboxd username and get a random film off your own watchlist.</p>

  <form class="form" @submit.prevent="findTonightsPick">
    <div class="field">
      <input
        v-model="username"
        type="text"
        placeholder="username"
        :disabled="loading"
        autocomplete="off"
      />
      <p v-if="fieldError" class="field-error">{{ fieldError }}</p>
    </div>
    <StreamingFilter :filter="streaming" />

    <button type="submit" :disabled="!canSubmit">
      {{ pendingAction === 'tonight' ? 'Searching…' : "🎲 Pick Something to Watch" }}
    </button>
    <button type="button" class="all-matches-button" :disabled="!canSubmit" @click="findAllFilms">
      {{ pendingAction === 'all' ? 'Searching…' : 'Return all films in my watchlist' }}
    </button>
  </form>

  <p v-if="loading" class="status loading">
    <span class="spinner" aria-hidden="true"></span>
    Scraping the watchlist, this can take a little while for large lists…
  </p>
  <p v-else-if="error" class="status error">{{ error }}</p>

  <template v-if="matches !== null && !loading">
    <p v-if="matches.length === 0" class="status">No films in this watchlist.</p>

    <template v-else-if="lastSearchWasRandom">
      <div class="picked-film">
        <img
          v-if="matches[0].posterUrl"
          :src="matches[0].posterUrl"
          :alt="matches[0].title"
          class="picked-poster"
        />
        <div v-else class="picked-poster poster-placeholder" aria-hidden="true"></div>
        <div class="picked-info">
          <p class="picked-label">Tonight's pick</p>
          <a
            :href="matches[0].url"
            target="_blank"
            rel="noopener noreferrer"
            class="picked-title"
          >{{ matches[0].title }}</a>
          <p v-if="pickMeta(matches[0])" class="picked-meta">{{ pickMeta(matches[0]) }}</p>

          <p v-if="pickStreamingNote?.warning" class="picked-streaming picked-streaming--warning">
            {{ pickStreamingNote.text }}
          </p>
          <div v-else-if="pickStreamingNote?.providers?.length" class="picked-streaming">
            <span class="picked-streaming-label">Streaming on</span>
            <span v-for="p in pickStreamingNote.providers" :key="p.id" class="picked-provider">
              <img v-if="p.logoUrl" :src="p.logoUrl" alt="" class="picked-provider-logo" />
              {{ p.name }}
            </span>
          </div>
        </div>
      </div>
      <p class="tmdb-attribution">
        Streaming data
        <a href="https://www.justwatch.com/" target="_blank" rel="noopener noreferrer">powered by JustWatch</a>
        · Posters from <a href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer">TMDB</a>
      </p>
    </template>

    <template v-else>
      <p v-if="clearedFilterForList" class="results-note">
        Streaming filter turned off — this is your whole watchlist.
      </p>
      <ul class="results">
        <li v-for="film in matches" :key="film.url">
          <a :href="film.url" target="_blank" rel="noopener noreferrer">
            <img v-if="film.posterUrl" :src="film.posterUrl" :alt="film.title" class="poster" />
            <div v-else class="poster poster-placeholder" aria-hidden="true"></div>
            <span class="poster-title">{{ film.title }}</span>
          </a>
        </li>
      </ul>
      <button type="button" class="download-button download-button-small" @click="downloadCsv">Download CSV</button>
      <p class="tmdb-attribution">Posters from <a href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer">TMDB</a></p>
    </template>
  </template>
</template>

<style scoped>
h1 {
  margin-bottom: 0.25rem;
}

.subtitle {
  color: #999;
  margin-top: 0;
  margin-bottom: 2rem;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field input {
  width: 100%;
}

.field-error {
  margin: 0;
  font-size: 0.85rem;
  color: #c0392b;
}

input,
button {
  font-size: 1rem;
  padding: 0.6rem 0.8rem;
  border-radius: 0.4rem;
  border: 1px solid #ccc;
}

input {
  background: #242424;
  color: #e0e0e0;
}

button {
  background: #4a8f63;
  color: #fff;
  border: none;
  cursor: pointer;
  font-weight: 600;
}

button:disabled {
  background: #3d5c48;
  cursor: not-allowed;
}

.download-button {
  margin-top: 0;
  background: #4a8f63;
  color: #e0e0e0;
  border: 1px solid #4a8f63;
}

.download-button:hover {
  background: #3d7a53;
}

.download-button-small {
  font-size: 0.8rem;
  padding: 0.4rem 0.6rem;
}

.all-matches-button {
  background: transparent;
  color: #4a8f63;
  border: none;
  font-size: 0.95rem;
  font-weight: 400;
  padding: 0;
  align-self: center;
  cursor: pointer;
}

.all-matches-button:disabled {
  background: transparent;
  color: #3d5c48;
  cursor: not-allowed;
}

.all-matches-button:hover {
  text-decoration: underline;
}

.picked-film {
  margin-top: 1.5rem;
  display: flex;
  align-items: center;
  gap: 1.25rem;
  padding: 1.5rem;
  border: 1px solid #4a8f63;
  border-radius: 0.75rem;
  background: #1a2620;
}

.picked-poster {
  width: 7rem;
  aspect-ratio: 2 / 3;
  border-radius: 0.5rem;
  object-fit: cover;
  background: #242424;
  flex-shrink: 0;
}

.picked-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.4rem;
}

.picked-label {
  margin: 0;
  font-size: 0.75rem;
  color: #999;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.picked-title {
  color: #e0e0e0;
  font-size: 1.1rem;
  font-weight: 600;
  text-decoration: none;
}

.picked-title:hover {
  color: #4a8f63;
}

.picked-meta {
  margin: 0.15rem 0 0;
  font-size: 0.85rem;
  color: #999;
}

.results-note {
  margin-top: 1.5rem;
  margin-bottom: -0.5rem;
  font-size: 0.8rem;
  color: #999;
}

.picked-streaming {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  margin: 0.35rem 0 0;
  font-size: 0.8rem;
  color: #cfe3d6;
}

.picked-streaming--warning {
  color: #d98c4a;
}

.picked-streaming-label {
  color: #999;
}

.picked-provider {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  background: #17211c;
  border: 1px solid #3d5c48;
  border-radius: 999px;
  padding: 0.15rem 0.55rem;
}

.picked-provider-logo {
  width: 0.9rem;
  height: 0.9rem;
  border-radius: 0.2rem;
  object-fit: cover;
}

.status {
  margin-top: 1.5rem;
}

.status.loading {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.spinner {
  width: 1rem;
  height: 1rem;
  flex-shrink: 0;
  border: 2px solid #2e3f34;
  border-top-color: #4a8f63;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.status.error {
  color: #c0392b;
}

.results {
  list-style: none;
  padding: 0;
  margin: 1.5rem 0 0.75rem;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
}

.results a {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  color: inherit;
  text-decoration: none;
}

.poster {
  width: 100%;
  aspect-ratio: 2 / 3;
  border-radius: 0.4rem;
  object-fit: cover;
  background: #242424;
}

.poster-placeholder {
  border: 1px solid #333;
}

.poster-title {
  font-size: 0.8rem;
  color: #e0e0e0;
  text-align: center;
  line-height: 1.3;
}

.results a:hover .poster-title {
  color: #4a8f63;
}

.tmdb-attribution {
  margin-top: 1.5rem;
  font-size: 0.75rem;
  color: #777;
  text-align: center;
}

.tmdb-attribution a {
  color: #777;
}

.tmdb-attribution a:hover {
  color: #4a8f63;
}

/* --- Mobile (keep the 640px breakpoint in sync with App.vue) --- */
@media (max-width: 640px) {
  h1 {
    font-size: 1.6rem;
  }

  .subtitle {
    margin-bottom: 1.25rem;
  }

  .results {
    grid-template-columns: repeat(2, 1fr);
  }

  /* Keep the poster and its details side by side -- just tighter and smaller. */
  .picked-film {
    gap: 0.9rem;
    padding: 1rem;
  }

  .picked-poster {
    width: 5rem;
  }

  .picked-label {
    font-size: 0.7rem;
  }

  .picked-title {
    font-size: 1rem;
  }

  .picked-meta {
    font-size: 0.8rem;
  }
}
</style>
