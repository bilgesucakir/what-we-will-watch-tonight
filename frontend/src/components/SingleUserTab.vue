<script setup>
import { ref, computed } from 'vue'
import { useUsernameCheck, usernameFieldError } from '../composables/useUsernameCheck'
import { useStreamingFilter, streamingNote } from '../composables/useStreamingFilter'
import { downloadFilmsAsCsv } from '../utils/csv'
import { PhPopcorn } from '@phosphor-icons/vue'
import StreamingFilter from './StreamingFilter.vue'
import SofaStage from './SofaStage.vue'
import FilmGrid from './FilmGrid.vue'
import PickCard from './PickCard.vue'
import ResultSkeleton from './ResultSkeleton.vue'
import UsernameInput from './UsernameInput.vue'

const username = ref('')
const loading = ref(false)
const error = ref('')
const matches = ref(null)
const pendingAction = ref(null)
const lastSearchWasRandom = ref(false)
const pickStreamingNote = ref(null)
// Set when "return all films" cleared an active streaming filter.
const clearedFilterForList = ref(false)

// "Pick something streamable" state, remembered in localStorage. Random pick only.
const streaming = useStreamingFilter()

// Username the current `matches` came from, captured so the CSV name stays right.
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

// Short summary for screen readers, read from one always-present live region
// (announcing the whole poster grid would be far too much).
const announcement = computed(() => {
  if (loading.value) return 'Scraping the watchlist…'
  if (error.value) return error.value
  if (matches.value === null) return ''
  if (matches.value.length === 0) return 'No films in this watchlist.'
  if (lastSearchWasRandom.value) return `Tonight's pick: ${matches.value[0].title}`
  return `${matches.value.length} films`
})

const listSummary = computed(() => {
  const n = matches.value?.length ?? 0
  return `${n} ${n === 1 ? 'film' : 'films'} in your watchlist`
})

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
      <UsernameInput v-model="username" label="Letterboxd username" :disabled="loading" />
      <p v-if="fieldError" class="field-error">{{ fieldError }}</p>
    </div>
    <StreamingFilter :filter="streaming" />

    <button type="submit" class="pick-button" :disabled="!canSubmit">
      <template v-if="pendingAction === 'tonight'">Searching…</template>
      <template v-else><PhPopcorn :size="20" weight="duotone" aria-hidden="true" />Pick Something to Watch</template>
    </button>
    <button type="button" class="all-matches-button" :disabled="!canSubmit" @click="findAllFilms">
      {{ pendingAction === 'all' ? 'Searching…' : 'Return all films in my watchlist' }}
    </button>
  </form>

  <p class="visually-hidden" aria-live="polite">{{ announcement }}</p>

  <ResultSkeleton v-if="loading" :kind="pendingAction === 'all' ? 'grid' : 'pick'">
    Scraping the watchlist, this can take a little while for large lists…
  </ResultSkeleton>
  <p v-else-if="error" class="status error">{{ error }}</p>

  <template v-if="matches !== null && !loading">
    <p v-if="matches.length === 0" class="status">No films in this watchlist.</p>

    <template v-else-if="lastSearchWasRandom">
      <PickCard :film="matches[0]" label="Tonight's pick">
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
      </PickCard>
      <p class="tmdb-attribution">
        Streaming data
        <a href="https://www.justwatch.com/" target="_blank" rel="noopener noreferrer">powered by JustWatch</a>
        · Posters from <a href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer">TMDB</a>
      </p>
    </template>

    <template v-else>
      <FilmGrid :films="matches" :summary="listSummary" @download="downloadCsv" />
      <p class="tmdb-attribution">Posters from <a href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer">TMDB</a></p>
    </template>
  </template>
</template>

<style scoped>
h1 {
  margin-bottom: 0.25rem;
}

.subtitle {
  color: var(--text-muted);
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

.field-error {
  margin: 0;
  font-size: 0.85rem;
  color: var(--danger);
}

button {
  font-size: 1rem;
  padding: 0.6rem 0.8rem;
  border-radius: var(--radius-control);
  border: 1px solid var(--line);
}

button {
  background: var(--accent);
  color: var(--on-accent);
  border: none;
  cursor: pointer;
  font-weight: 600;
}

.pick-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
}

.pick-button:not(:disabled):hover {
  background: var(--accent-hover);
}

.all-matches-button {
  background: transparent;
  color: var(--accent-text);
  border: none;
  font-size: 0.95rem;
  font-weight: 400;
  padding: 0;
  align-self: center;
  cursor: pointer;
}

.all-matches-button:disabled {
  background: transparent;
}

.all-matches-button:hover {
  text-decoration: underline;
}

.picked-streaming {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  margin: 0.35rem 0 0;
  font-size: 0.8rem;
  color: var(--text);
}

.picked-streaming--warning {
  color: var(--warning);
}

.picked-streaming-label {
  color: var(--text-muted);
}

.picked-provider {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  background: var(--accent-soft);
  border: 1px solid var(--accent-line);
  border-radius: var(--radius-pill);
  padding: 0.15rem 0.55rem;
}

.picked-provider-logo {
  width: 0.9rem;
  height: 0.9rem;
  border-radius: var(--radius-logo);
  object-fit: cover;
}

.status {
  margin-top: 1.5rem;
}

.status.error {
  color: var(--danger);
}

.tmdb-attribution {
  margin-top: 1.5rem;
  font-size: 0.75rem;
  color: var(--text-faint);
  text-align: center;
}

.tmdb-attribution a {
  color: var(--text-faint);
}

.tmdb-attribution a:hover {
  color: var(--accent-text);
}

/* --- Mobile (keep the 640px breakpoint in sync with App.vue) --- */
@media (max-width: 640px) {
  h1 {
    font-size: 1.6rem;
  }

  .subtitle {
    margin-bottom: 1.25rem;
  }
}
</style>
