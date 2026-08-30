<script setup>
import { REGIONS } from '../composables/useStreamingFilter'

// The shared state object returned by useStreamingFilter(), created in the
// parent tab so the parent can read it when building the pick request.
defineProps({
  filter: { type: Object, required: true }
})
</script>

<template>
  <div class="streaming">
    <label class="streaming-toggle">
      <input type="checkbox" v-model="filter.enabled.value" />
      Only pick something we can stream
    </label>

    <div v-if="filter.enabled.value" class="streaming-body">
      <label class="streaming-region">
        Region
        <select v-model="filter.region.value">
          <option v-if="filter.needsRegion.value" :value="null" disabled>Choose your country…</option>
          <option v-for="r in REGIONS" :key="r.code" :value="r.code">{{ r.name }}</option>
        </select>
      </label>

      <p v-if="filter.needsRegion.value" class="streaming-hint">
        We couldn't tell where you are from your browser — pick a country to see its services.
      </p>
      <p v-else-if="filter.loading.value" class="streaming-hint">Loading services…</p>
      <p v-else-if="filter.providers.value.length === 0" class="streaming-hint">
        No streaming data available for this region.
      </p>

      <template v-else>
        <p class="streaming-hint">Tick the services you have:</p>
        <div class="streaming-chips">
          <button
            v-for="p in filter.providers.value"
            :key="p.id"
            type="button"
            class="chip"
            :class="{ 'chip--on': filter.selectedIds.value.includes(p.id) }"
            @click="filter.toggle(p.id)"
          >
            <img v-if="p.logoUrl" :src="p.logoUrl" alt="" class="chip-logo" />
            {{ p.name }}
          </button>
        </div>

        <button
          v-if="filter.selectedIds.value.length"
          type="button"
          class="streaming-clear"
          @click="filter.clear()"
        >
          Clear selection
        </button>
      </template>

      <p class="streaming-scope">
        Applies to the 🎲 random pick only
      </p>
    </div>
  </div>
</template>

<style scoped>
/*
 * The checkbox + label always sit outside any chrome, a lightweight sibling of
 * the "+ Add person" control above. When ticked, only the region + chips get
 * wrapped in the bordered card -- the tick itself is never boxed in.
 */
.streaming {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.streaming-toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #cfe3d6;
  font-weight: 500;
  font-size: 0.9rem;
  padding: 0.35rem 0.2rem;
  cursor: pointer;
}

.streaming-toggle input {
  width: auto;
  padding: 0;
  cursor: pointer;
}

.streaming-body {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.8rem;
  border: 1px solid #4a8f63;
  border-radius: 0.5rem;
  background: #17211c;
}

.streaming-region {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: #999;
}

.streaming-region select {
  flex: 1;
  font-size: 0.85rem;
  padding: 0.35rem 0.5rem;
  border-radius: 0.4rem;
  background: #242424;
  color: #e0e0e0;
  border: 1px solid #4a4a4a;
}

.streaming-hint {
  margin: 0;
  font-size: 0.8rem;
  color: #999;
}

.streaming-scope {
  margin: 0.15rem 0 0;
  padding-top: 0.5rem;
  border-top: 1px solid #2a3a30;
  font-size: 0.75rem;
  color: #888;
}

.streaming-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: #242424;
  color: #cfcfcf;
  border: 1px solid #4a4a4a;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 400;
  padding: 0.3rem 0.7rem;
}

.chip--on {
  background: #4a8f63;
  color: #fff;
  border-color: #4a8f63;
}

.chip-logo {
  width: 1rem;
  height: 1rem;
  border-radius: 0.2rem;
  object-fit: cover;
}

.streaming-clear {
  align-self: flex-start;
  background: transparent;
  color: #4a8f63;
  border: none;
  font-size: 0.8rem;
  font-weight: 400;
  padding: 0;
}

.streaming-clear:hover {
  text-decoration: underline;
  background: transparent;
}
</style>
