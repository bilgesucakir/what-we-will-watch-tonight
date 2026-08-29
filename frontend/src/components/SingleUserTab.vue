<script setup>
import { ref, computed } from 'vue'
import { useUsernameCheck, usernameFieldError } from '../composables/useUsernameCheck'
import { downloadFilmsAsCsv } from '../utils/csv'
import { pickMeta } from '../utils/format'

const username = ref('')
const loading = ref(false)
const error = ref('')
const matches = ref(null)
const pendingAction = ref(null)
const lastSearchWasRandom = ref(false)

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
  lastSearchWasRandom.value = random

  const trimmed = username.value.trim()
  if (!trimmed) return

  loading.value = true
  pendingAction.value = random ? 'tonight' : 'all'

  const params = new URLSearchParams({ user: trimmed })
  if (random) params.set('random', 'true')

  try {
    const response = await fetch(`/api/watchlist?${params}`)
    const body = await response.json()

    if (!response.ok) {
      error.value = body.error || 'Something went wrong.'
      return
    }

    matches.value = body
    searchedUsername.value = trimmed
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
  <div class="sofa-seats" aria-hidden="true">
    <Transition name="seat-pop">
      <img v-if="avatarUrl" :src="avatarUrl" alt="" class="seat seat--solo" />
    </Transition>
  </div>

  <h1>What I'll Watch Tonight</h1>
  <p class="subtitle">Pick something at random from just your own Letterboxd watchlist.</p>

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
        </div>
      </div>
      <p class="tmdb-attribution">Posters from <a href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer">TMDB</a></p>
    </template>

    <template v-else>
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
/*
 * Seats the verified avatar on the armchair in the background image.
 * .sofa-seats is a fixed overlay sized to exactly match the scene image in
 * App.vue's `.app-background` (background-size: cover, pinned bottom-right,
 * 1760x1040 png), so the offsets below are plain percentages of the image,
 * measured from its right / bottom edges.
 */
.sofa-seats {
  position: fixed;
  right: 0;
  bottom: 0;
  width: max(100vw, calc(100vh * 1760 / 1040));
  height: max(100vh, calc(100vw * 1040 / 1760));
  z-index: -1;
  pointer-events: none;
}

.seat {
  position: absolute;
  width: 5%;
  aspect-ratio: 1;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid #fff;
  box-shadow: 0 0.5rem 1.1rem rgba(0, 0, 0, 0.55);
  transform: translate(50%, 50%);
}

/* Centered on the single armchair cushion. */
.seat--solo {
  right: 12.35%;
  bottom: 20%;
}

/* Drop into the seat when the username checks out. */
.seat-pop-enter-active {
  transition: opacity 0.35s ease, transform 0.4s cubic-bezier(0.2, 1.4, 0.4, 1);
}

.seat-pop-leave-active {
  transition: opacity 0.2s ease;
}

.seat-pop-enter-from {
  opacity: 0;
  transform: translate(50%, 140%);
}

.seat-pop-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .seat-pop-enter-active {
    transition: opacity 0.2s ease;
  }
  .seat-pop-enter-from {
    transform: translate(50%, 50%);
  }
}

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
  margin-top: 1.5rem;
  background: #e0e0e0;
  color: #4a8f63;
  border: 1px solid #4a8f63;
}

.download-button:hover {
  background: #cbe0d1;
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
  margin-top: 1.5rem;
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
</style>
