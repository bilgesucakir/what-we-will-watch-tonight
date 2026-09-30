<script setup>
import { pickMeta } from '../utils/format'

/**
 * The single-film result. A blurred copy of the poster fills the card so
 * every pick gets its own glow; extra lines (streaming info) go in the slot.
 */
defineProps({
  film: { type: Object, required: true },
  label: { type: String, required: true }
})
</script>

<template>
  <div class="picked-film">
    <div
      v-if="film.posterUrl"
      class="picked-glow"
      :style="{ backgroundImage: `url(${film.posterUrl})` }"
      aria-hidden="true"
    ></div>
    <div class="picked-content">
      <img
        v-if="film.posterUrl"
        :src="film.posterUrl"
        :alt="film.title"
        class="picked-poster"
        width="342"
        height="513"
      />
      <div v-else class="picked-poster poster-placeholder" aria-hidden="true"></div>
      <div class="picked-info">
        <p class="picked-label">{{ label }}</p>
        <a :href="film.url" target="_blank" rel="noopener noreferrer" class="picked-title">{{
          film.title
        }}</a>
        <p v-if="pickMeta(film)" class="picked-meta">{{ pickMeta(film) }}</p>
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.picked-film {
  position: relative;
  margin-top: 1.5rem;
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--accent-soft);
}

/* Oversized and blurred so the edges never show; darkened to keep text readable. */
.picked-glow {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  filter: blur(22px) brightness(0.45) saturate(1.2);
  transform: scale(1.2);
}

.picked-content {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 1.25rem;
  padding: 1.25rem;
}

.picked-poster {
  width: 7.5rem;
  height: auto;
  aspect-ratio: 2 / 3;
  border-radius: var(--radius-control);
  object-fit: cover;
  background: var(--surface);
  flex-shrink: 0;
  box-shadow: 0 0.6rem 1.5rem rgba(3, 6, 14, 0.6);
}

.poster-placeholder {
  border: 1px solid var(--line);
}

.picked-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.4rem;
  min-width: 0;
}

.picked-label {
  margin: 0;
  font-size: 0.75rem;
  color: var(--text-soft);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.picked-title {
  color: var(--text);
  font-size: 1.25rem;
  font-weight: 600;
  line-height: 1.2;
  text-decoration: none;
}

.picked-title:hover {
  color: var(--accent-text);
}

.picked-meta {
  margin: 0.15rem 0 0;
  font-size: 0.85rem;
  color: var(--text-soft);
}

/* --- Mobile (keep the 640px breakpoint in sync with App.vue) --- */
@media (max-width: 640px) {
  .picked-content {
    gap: 0.9rem;
    padding: 1rem;
  }

  .picked-poster {
    width: 6rem;
  }

  .picked-label {
    font-size: 0.7rem;
  }

  .picked-title {
    font-size: 1.1rem;
  }

  .picked-meta {
    font-size: 0.8rem;
  }
}
</style>
