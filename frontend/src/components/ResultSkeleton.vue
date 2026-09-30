<script setup>
/**
 * Placeholder shown while the watchlists are scraped, shaped like the result
 * that's coming: the pick card for a random pick, the poster grid otherwise.
 */
defineProps({
  kind: { type: String, default: 'pick' } // 'pick' | 'grid'
})

// One row: three across on desktop; the third hides on phones (two columns).
const GRID_TILES = 3
</script>

<template>
  <div class="skeleton" aria-busy="true">
    <div v-if="kind === 'pick'" class="skeleton-pick">
      <div class="sk skeleton-poster"></div>
      <div class="skeleton-lines">
        <div class="sk" style="height: 0.6rem; width: 40%"></div>
        <div class="sk" style="height: 1.1rem; width: 75%"></div>
        <div class="sk" style="height: 0.7rem; width: 55%"></div>
      </div>
    </div>
    <div v-else class="skeleton-grid">
      <div v-for="n in GRID_TILES" :key="n" class="sk skeleton-tile"></div>
    </div>
    <p class="skeleton-status"><slot /></p>
  </div>
</template>

<style scoped>
.skeleton {
  margin-top: 1.5rem;
}

.sk {
  border-radius: var(--radius-control);
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

.skeleton-pick {
  display: flex;
  align-items: flex-end;
  gap: 1.25rem;
  padding: 1.25rem;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-card);
}

.skeleton-poster {
  width: 7.5rem;
  aspect-ratio: 2 / 3;
  border-radius: var(--radius-control);
  flex-shrink: 0;
}

.skeleton-lines {
  flex: 1;
  display: grid;
  gap: 0.5rem;
}

.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
}

.skeleton-tile {
  aspect-ratio: 2 / 3;
}

.skeleton-status {
  margin: 0.75rem 0 0;
  font-size: 0.85rem;
  color: var(--text-muted);
}

@media (prefers-reduced-motion: reduce) {
  .sk {
    animation: none;
  }
}

/* --- Mobile (keep the 640px breakpoint in sync with App.vue) --- */
@media (max-width: 640px) {
  .skeleton-pick {
    gap: 0.9rem;
    padding: 1rem;
  }

  .skeleton-poster {
    width: 6rem;
  }

  .skeleton-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .skeleton-tile:nth-child(3) {
    display: none;
  }
}
</style>
