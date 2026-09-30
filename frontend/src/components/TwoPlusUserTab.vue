<script setup>
import { reactive, ref, computed, toRef } from 'vue'
import { useUsernameCheck, usernameFieldError } from '../composables/useUsernameCheck'
import { useStreamingFilter, streamingNote } from '../composables/useStreamingFilter'
import { downloadFilmsAsCsv } from '../utils/csv'
import { PhPopcorn } from '@phosphor-icons/vue'
import StreamingFilter from './StreamingFilter.vue'
import SofaStage from './SofaStage.vue'
import PickCard from './PickCard.vue'
import ResultSkeleton from './ResultSkeleton.vue'
import UsernameInput from './UsernameInput.vue'

const MIN_PEOPLE = 2
const MAX_PEOPLE = 4

// Always MAX_PEOPLE username slots; `count` decides how many are shown / used.
const names = reactive(Array.from({ length: MAX_PEOPLE }, () => ''))
const count = ref(MIN_PEOPLE)

const activeIndexes = computed(() => Array.from({ length: count.value }, (_, i) => i))
const activeNames = computed(() => activeIndexes.value.map((i) => names[i].trim()))

// True when field `index` repeats an earlier active field's username
// (case-insensitive).
function isRepeat(index) {
  const name = names[index].trim().toLowerCase()
  return (
    name !== '' &&
    activeIndexes.value.some((other) => other < index && names[other].trim().toLowerCase() === name)
  )
}

// One existence/avatar check per slot; a repeated username is never fetched.
const checks = names.map((_, index) => useUsernameCheck(toRef(names, index), () => !isRepeat(index)))

// Avatar URLs for the active people, in order, for the sofa banner.
const seatedAvatars = computed(() => activeIndexes.value.map((i) => checks[i].avatarUrl.value))

// "Pick something streamable" state, remembered in localStorage. Random pick only.
const streaming = useStreamingFilter()

const loading = ref(false)
const error = ref('')
const matches = ref(null)
const surprisePick = ref(null)
const pendingAction = ref(null)
const lastSearchWasRandom = ref(false)
const pickStreamingNote = ref(null)
// Set when "return all films" cleared an active streaming filter.
const clearedFilterForList = ref(false)

// Usernames the current `matches` came from, captured so the CSV name stays right.
const searchedNames = ref([])

const hasEmptyField = computed(() => activeNames.value.some((name) => name === ''))
const hasDuplicates = computed(() => {
  const filled = activeNames.value.filter(Boolean).map((name) => name.toLowerCase())
  return new Set(filled).size !== filled.length
})
const canSubmit = computed(
  () =>
    !hasEmptyField.value &&
    !hasDuplicates.value &&
    activeIndexes.value.every((i) => checks[i].watchlistPublic.value === true) &&
    !loading.value
)

function fieldError(index) {
  if (isRepeat(index)) {
    return 'This username is already in the list.'
  }
  return usernameFieldError(checks[index].exists.value, checks[index].watchlistPublic.value)
}

function addPerson() {
  if (count.value < MAX_PEOPLE) {
    count.value += 1
  }
}

function removePerson(index) {
  for (let j = index; j < count.value - 1; j++) {
    names[j] = names[j + 1]
  }
  names[count.value - 1] = ''
  count.value -= 1
}

async function search(random) {
  error.value = ''
  matches.value = null
  surprisePick.value = null
  pickStreamingNote.value = null
  clearedFilterForList.value = false
  lastSearchWasRandom.value = random

  // The buttons are disabled in these states; guard anyway.
  if (hasEmptyField.value || hasDuplicates.value) return

  // The full list is never streaming-filtered -- asking for it switches the
  // filter off and drops the selection.
  if (!random && streaming.enabled.value) {
    streaming.enabled.value = false
    streaming.clear()
    clearedFilterForList.value = true
  }

  const users = activeNames.value
  loading.value = true
  pendingAction.value = random ? 'tonight' : 'all'

  const params = new URLSearchParams()
  users.forEach((user) => params.append('user', user))
  if (random) {
    params.set('random', 'true')
    // region + provider ids, only when the streaming filter is switched on
    streaming.pickParams().forEach(([key, value]) => params.append(key, value))
  }

  try {
    const response = await fetch(`/api/intersect?${params}`)
    const body = await response.json()

    if (!response.ok) {
      error.value = body.error || 'Something went wrong.'
      return
    }

    matches.value = body
    searchedNames.value = users

    if (random && body.length > 0) {
      pickStreamingNote.value = streamingNote(body[0], streaming)
    }

    if (body.length === 0) {
      const surpriseResponse = await fetch('/api/underwatched-pick')
      if (surpriseResponse.ok) {
        surprisePick.value = await surpriseResponse.json()
      }
    }
  } catch (e) {
    error.value = 'Could not reach the server. Please try again.'
  } finally {
    loading.value = false
    pendingAction.value = null
  }
}

function findAllMatches() {
  return search(false)
}

function findTonightsPick() {
  return search(true)
}

// Short summary for screen readers, read from one always-present live region
// (announcing the whole poster grid would be far too much).
const announcement = computed(() => {
  if (loading.value) return 'Scraping the watchlists…'
  if (error.value) return error.value
  if (matches.value === null) return ''
  if (matches.value.length === 0) {
    return surprisePick.value ? `Nothing in common. An underwatched pick: ${surprisePick.value.title}` : 'No films in common.'
  }
  if (lastSearchWasRandom.value) return `Tonight's pick: ${matches.value[0].title}`
  return `${matches.value.length} films`
})

function downloadCsv() {
  downloadFilmsAsCsv(matches.value, `${searchedNames.value.join('_')}_watchlist_intersection.csv`)
}
</script>

<template>
  <SofaStage :count="count" :avatars="seatedAvatars" />

  <h1>What We'll Watch Tonight</h1>
  <p class="subtitle">
    Enter your Letterboxd usernames and get one film that's on all of your watchlists.
  </p>

  <form class="form" @submit.prevent="findTonightsPick">
    <div v-for="index in count" :key="index - 1" class="field">
      <UsernameInput
        v-model="names[index - 1]"
        :label="`Person ${index} Letterboxd username`"
        :disabled="loading"
      >
        <button
          v-if="index > MIN_PEOPLE"
          type="button"
          class="remove-person"
          :aria-label="`Remove person ${index}`"
          @click="removePerson(index - 1)"
        >
          &times;
        </button>
      </UsernameInput>
      <p v-if="fieldError(index - 1)" class="field-error">{{ fieldError(index - 1) }}</p>
    </div>

    <button v-if="count < MAX_PEOPLE" type="button" class="add-person" @click="addPerson">
      + Add person
    </button>

    <StreamingFilter :filter="streaming" />

    <button type="submit" class="pick-button" :disabled="!canSubmit">
      <template v-if="pendingAction === 'tonight'">Searching…</template>
      <template v-else><PhPopcorn :size="20" weight="duotone" aria-hidden="true" />Pick Something to Watch</template>
    </button>
    <button type="button" class="all-matches-button" :disabled="!canSubmit" @click="findAllMatches">
      {{ pendingAction === 'all' ? 'Searching…' : 'Return all films everyone has in common' }}
    </button>
  </form>

  <p class="visually-hidden" aria-live="polite">{{ announcement }}</p>

  <ResultSkeleton v-if="loading" :kind="pendingAction === 'all' ? 'grid' : 'pick'">
    Scraping the watchlists, this can take a little while for large lists…
  </ResultSkeleton>

  <p v-if="error" class="status error">{{ error }}</p>

  <template v-if="matches !== null && !loading">
    <template v-if="matches.length === 0 && surprisePick">
      <p class="surprise-intro">
        Nothing in common in your watchlists, but I bet none of you have seen this:
      </p>
      <PickCard :film="surprisePick" label="An underwatched pick" />
      <p class="tmdb-attribution">
        From
        <a href="https://letterboxd.com/official/list/top-100-underseen-films/" target="_blank" rel="noopener noreferrer">Letterboxd's Top 100 Underseen Films</a>.
        Posters from <a href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer">TMDB</a>
      </p>
    </template>

    <p v-else-if="matches.length === 0" class="status">No films in common.</p>

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
      <ul class="results">
        <li v-for="film in matches" :key="film.url">
          <a :href="film.url" target="_blank" rel="noopener noreferrer">
            <img
              v-if="film.posterUrl"
              :src="film.posterUrl"
              :alt="film.title"
              class="poster"
              width="342"
              height="513"
              loading="lazy"
            />
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

/* Sits inside the username box on the right, split off by a thin divider. */
.remove-person {
  width: 2.5rem;
  flex-shrink: 0;
  padding: 0;
  font-size: 1.15rem;
  line-height: 1;
  color: var(--text-muted);
  background: transparent;
  border: none;
  border-left: 1px solid var(--line);
  border-radius: 0;
}

.remove-person:hover {
  color: var(--text);
  background: transparent;
}

.add-person {
  align-self: flex-start;
  background: transparent;
  color: var(--accent-text);
  border: 1px dashed var(--accent-line);
  font-size: 0.9rem;
  padding: 0.4rem 0.8rem;
}

.add-person:hover {
  background: var(--accent-soft);
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

.download-button {
  margin-top: 0;
  background: var(--accent);
  color: var(--text);
  border: 1px solid var(--accent);
}

.download-button:hover {
  background: var(--accent-hover);
}

.download-button-small {
  font-size: 0.8rem;
  padding: 0.4rem 0.6rem;
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

.surprise-intro {
  margin-top: 1.5rem;
  margin-bottom: 0;
  color: var(--text);
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
  height: auto;
  aspect-ratio: 2 / 3;
  border-radius: var(--radius-control);
  object-fit: cover;
  background: var(--surface);
}

.poster-placeholder {
  border: 1px solid var(--line);
}

.poster-title {
  font-size: 0.8rem;
  color: var(--text);
  text-align: center;
  line-height: 1.3;
}

.results a:hover .poster-title {
  color: var(--accent-text);
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

  .results {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
