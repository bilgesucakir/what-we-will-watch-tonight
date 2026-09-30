<script setup>
import { ref, reactive, computed, watch } from 'vue'

/**
 * The full list as a poster grid, PAGE_SIZE films at a time. The list arrives
 * without posters (so it's fast even for huge watchlists); each page's posters
 * are fetched from /api/posters as it's shown, and kept for the session.
 */
const PAGE_SIZE = 24

const props = defineProps({
  films: { type: Array, required: true }, // { url, title, year } as /api/intersect or /api/watchlist returned them
  summary: { type: String, required: true } // e.g. "502 films in common", shown next to the download button
})

// The parent owns the CSV (it knows the usernames for the file name).
defineEmits(['download'])

const shownCount = ref(PAGE_SIZE)
// url -> poster URL, or null when there isn't one. Missing = still loading.
const posters = reactive(new Map())

const shownFilms = computed(() => props.films.slice(0, shownCount.value))
const remaining = computed(() => props.films.length - shownFilms.value.length)

async function loadPosters(films) {
  const missing = films.filter((film) => !posters.has(film.url))
  if (missing.length === 0) return
  // Mark them first so a quick second "Show more" doesn't ask twice.
  missing.forEach((film) => posters.set(film.url, undefined))

  let found = []
  try {
    const response = await fetch('/api/posters', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(missing.map(({ url, title, year }) => ({ url, title, year })))
    })
    if (response.ok) found = await response.json()
  } catch (e) {
    // Fall through: every film in this batch shows the no-poster placeholder.
  }

  const byUrl = new Map(found.map((entry) => [entry.url, entry.posterUrl]))
  missing.forEach((film) => posters.set(film.url, byUrl.get(film.url) ?? null))
}

// A new result list starts again from the first page.
watch(
  () => props.films,
  () => {
    shownCount.value = PAGE_SIZE
    posters.clear()
  }
)

watch(shownFilms, (films) => loadPosters(films), { immediate: true })

function showMore() {
  shownCount.value += PAGE_SIZE
}
</script>

<template>
  <div class="list-bar">
    <p class="list-summary">{{ summary }}</p>
    <button type="button" class="download-button button-link" @click="$emit('download')">
      Download CSV
    </button>
  </div>

  <ul class="results">
    <li v-for="film in shownFilms" :key="film.url">
      <a :href="film.url" target="_blank" rel="noopener noreferrer">
        <img
          v-if="posters.get(film.url)"
          :src="posters.get(film.url)"
          :alt="film.title"
          class="poster"
          width="342"
          height="513"
          loading="lazy"
        />
        <div
          v-else
          :class="['poster', posters.get(film.url) === null ? 'poster-placeholder' : 'poster-loading']"
          aria-hidden="true"
        ></div>
        <span class="poster-title">{{ film.title }}</span>
      </a>
    </li>
  </ul>

  <div v-if="remaining > 0" class="more">
    <button type="button" class="show-more button-link" @click="showMore">
      Show {{ Math.min(PAGE_SIZE, remaining) }} more
    </button>
    <p class="more-count">Showing {{ shownFilms.length }} of {{ films.length }}</p>
  </div>
</template>

<style scoped>
.list-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 1.5rem;
}

.list-summary {
  margin: 0;
  font-size: 0.9rem;
  color: var(--text-muted);
}

.results {
  list-style: none;
  padding: 0;
  margin: 1rem 0 0;
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

.poster-loading {
  background: linear-gradient(90deg, var(--surface-2) 0%, var(--surface-3) 50%, var(--surface-2) 100%);
  background-size: 200% 100%;
  animation: shimmer 1.3s ease-in-out infinite;
}

@keyframes shimmer {
  from {
    background-position: 100% 0;
  }
  to {
    background-position: -100% 0;
  }
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

.more {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.3rem;
  margin-top: 1.25rem;
}

.more-count {
  margin: 0;
  font-size: 0.8rem;
  color: var(--text-muted);
}

@media (prefers-reduced-motion: reduce) {
  .poster-loading {
    animation: none;
  }
}

/* --- Mobile (keep the 640px breakpoint in sync with App.vue) --- */
@media (max-width: 640px) {
  .results {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
