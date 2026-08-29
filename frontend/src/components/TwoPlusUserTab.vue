<script setup>
import { reactive, ref, computed, toRef, watch } from 'vue'
import { useUsernameCheck, usernameFieldError } from '../composables/useUsernameCheck'
import { useStreamingFilter, streamingNote } from '../composables/useStreamingFilter'
import { downloadFilmsAsCsv } from '../utils/csv'
import { pickMeta } from '../utils/format'
import StreamingFilter from './StreamingFilter.vue'

const MIN_PEOPLE = 2
const MAX_PEOPLE = 4

const emit = defineEmits(['sofa-count'])

// Always MAX_PEOPLE username slots; `count` decides how many are shown / used.
const names = reactive(Array.from({ length: MAX_PEOPLE }, () => ''))
const count = ref(MIN_PEOPLE)

const activeIndexes = computed(() => Array.from({ length: count.value }, (_, i) => i))
const activeNames = computed(() => activeIndexes.value.map((i) => names[i].trim()))

// True when field `index` repeats a username already entered in an earlier
// active field (case-insensitive).
function isRepeat(index) {
  const name = names[index].trim().toLowerCase()
  return (
    name !== '' &&
    activeIndexes.value.some((other) => other < index && names[other].trim().toLowerCase() === name)
  )
}

// One existence/avatar check per slot; a repeated username is never fetched.
const checks = names.map((_, index) => useUsernameCheck(toRef(names, index), () => !isRepeat(index)))

// Let the parent swap the sofa background to match the group size.
watch(count, (n) => emit('sofa-count', n), { immediate: true })

// "Only pick something we can stream" -- region + services + on/off, remembered
// in localStorage. Only affects the random pick.
const streaming = useStreamingFilter()

const loading = ref(false)
const error = ref('')
const matches = ref(null)
const surprisePick = ref(null)
const pendingAction = ref(null)
const lastSearchWasRandom = ref(false)
const pickStreamingNote = ref(null)
// Set when "return all films" cleared an active streaming filter, so the list
// view can say so.
const clearedFilterForList = ref(false)

// The usernames the current `matches` came from, captured at search time so the
// CSV filename stays right even if the inputs are edited afterwards.
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

// Circle diameter + per-cushion offsets (percentages of the background image,
// from its right / bottom edges), matched to sofa-for-two/three/four.png.
const SEAT_LAYOUTS = {
  2: { width: 4.5, bottom: 20, rights: [20, 12.5] },
  3: { width: 4.5, bottom: 20, rights: [26.5, 19.5, 12.5] },
  4: { width: 4.5, bottom: 20, rights: [33.5, 26.5, 19.5, 12.5] }
}

function seatStyle(index) {
  const layout = SEAT_LAYOUTS[count.value]
  return {
    width: `${layout.width}%`,
    right: `${layout.rights[index]}%`,
    bottom: `${layout.bottom}%`
  }
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
  // filter off and drops the selection so there's no lingering "is this
  // filtered?" ambiguity.
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

function downloadCsv() {
  downloadFilmsAsCsv(matches.value, `${searchedNames.value.join('_')}_watchlist_intersection.csv`)
}
</script>

<template>
  <div class="sofa-seats" aria-hidden="true">
    <template v-for="index in count" :key="index - 1">
      <Transition name="seat-pop">
        <img
          v-if="checks[index - 1].avatarUrl.value"
          :src="checks[index - 1].avatarUrl.value"
          alt=""
          class="seat"
          :style="seatStyle(index - 1)"
        />
      </Transition>
    </template>
  </div>

  <h1>What We'll Watch Tonight</h1>
  <p class="subtitle">
    Pick something from everyone's Letterboxd watchlists — or see everything you have in common.
  </p>

  <form class="form" @submit.prevent="findTonightsPick">
    <div v-for="index in count" :key="index - 1" class="field">
      <div class="field-input">
        <input
          v-model="names[index - 1]"
          type="text"
          placeholder="username"
          :disabled="loading"
          autocomplete="off"
        />
        <button
          v-if="index > MIN_PEOPLE"
          type="button"
          class="remove-person"
          :aria-label="`Remove person ${index}`"
          @click="removePerson(index - 1)"
        >
          &times;
        </button>
      </div>
      <p v-if="fieldError(index - 1)" class="field-error">{{ fieldError(index - 1) }}</p>
    </div>

    <button v-if="count < MAX_PEOPLE" type="button" class="add-person" @click="addPerson">
      + Add person
    </button>

    <StreamingFilter :filter="streaming" />

    <button type="submit" :disabled="!canSubmit">
      {{ pendingAction === 'tonight' ? 'Searching…' : '🎲 Pick Something to Watch' }}
    </button>
    <button type="button" class="all-matches-button" :disabled="!canSubmit" @click="findAllMatches">
      {{ pendingAction === 'all' ? 'Searching…' : 'Return all films everyone has in common' }}
    </button>
  </form>

  <p v-if="loading" class="status loading">
    <span class="spinner" aria-hidden="true"></span>
    Scraping the watchlists, this can take a little while for large lists…
  </p>

  <p v-if="error" class="status error">{{ error }}</p>

  <template v-if="matches !== null && !loading">
    <template v-if="matches.length === 0 && surprisePick">
      <p class="surprise-intro">
        Nothing in common in your watchlists — but I bet none of you have seen this:
      </p>
      <div class="picked-film">
        <img
          v-if="surprisePick.posterUrl"
          :src="surprisePick.posterUrl"
          :alt="surprisePick.title"
          class="picked-poster"
        />
        <div v-else class="picked-poster poster-placeholder" aria-hidden="true"></div>
        <div class="picked-info">
          <p class="picked-label">An underwatched pick</p>
          <a
            :href="surprisePick.url"
            target="_blank"
            rel="noopener noreferrer"
            class="picked-title"
          >{{ surprisePick.title }}</a>
          <p v-if="pickMeta(surprisePick)" class="picked-meta">{{ pickMeta(surprisePick) }}</p>
        </div>
      </div>
      <p class="tmdb-attribution">
        From
        <a href="https://letterboxd.com/official/list/top-100-underseen-films/" target="_blank" rel="noopener noreferrer">Letterboxd's Top 100 Underseen Films</a>.
        Posters from <a href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer">TMDB</a>
      </p>
    </template>

    <p v-else-if="matches.length === 0" class="status">No films in common.</p>

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
        Streaming filter turned off — this is everything everyone's watchlists share.
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
/*
 * Seats each verified avatar on a cushion of the background sofa. .sofa-seats
 * is a fixed overlay sized to exactly match App.vue's `.app-background`
 * (background-size: cover, pinned bottom-right, 1760x1040 png); the per-seat
 * right / bottom / width come from SEAT_LAYOUTS in the script, one layout per
 * sofa size.
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
  aspect-ratio: 1;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid #fff;
  box-shadow: 0 0.5rem 1.1rem rgba(0, 0, 0, 0.55);
  transform: translate(50%, 50%);
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

.field-input {
  position: relative;
  display: flex;
}

.field-input input {
  width: 100%;
}

/* Make room for the inline remove button only on rows that have one. */
.field-input:has(.remove-person) input {
  padding-right: 2.75rem;
}

/*
 * The remove button sits inside the input on the right, split off by a thin
 * divider rather than carrying its own button chrome.
 */
.remove-person {
  position: absolute;
  top: 1px;
  right: 1px;
  bottom: 1px;
  width: 2.5rem;
  padding: 0;
  font-size: 1.15rem;
  line-height: 1;
  color: #888;
  background: transparent;
  border: none;
  border-left: 1px solid #4a4a4a;
  border-radius: 0;
}

.remove-person:hover {
  color: #e0e0e0;
  background: transparent;
}

.add-person {
  align-self: flex-start;
  background: transparent;
  color: #4a8f63;
  border: 1px dashed #3d5c48;
  font-size: 0.9rem;
  padding: 0.4rem 0.8rem;
}

.add-person:hover {
  background: #1a2620;
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

.surprise-intro {
  margin-top: 1.5rem;
  margin-bottom: 0;
  color: #e0e0e0;
}

.results-note {
  margin-top: 1.5rem;
  margin-bottom: -0.5rem;
  font-size: 0.8rem;
  color: #999;
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
