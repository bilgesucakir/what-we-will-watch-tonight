<script setup>
/**
 * A Letterboxd username box with a fixed `letterboxd.com/` prefix, which
 * doubles as the field's visible label. The slot holds a trailing button
 * (the "remove person" ×) inside the same box.
 */
const model = defineModel({ type: String, required: true })

defineProps({
  label: { type: String, required: true }, // accessible name, e.g. "Person 2 Letterboxd username"
  disabled: { type: Boolean, default: false }
})
</script>

<template>
  <div class="username-input" :class="{ 'is-disabled': disabled }">
    <span class="prefix" aria-hidden="true">letterboxd.com/</span>
    <input
      v-model="model"
      type="text"
      placeholder="username"
      :aria-label="label"
      :disabled="disabled"
      autocomplete="off"
      autocapitalize="off"
      spellcheck="false"
    />
    <slot />
  </div>
</template>

<style scoped>
.username-input {
  display: flex;
  align-items: stretch;
  border: 1px solid var(--line);
  border-radius: var(--radius-control);
  background: var(--surface);
  overflow: hidden;
}

/* The inner input drops its own outline; the whole box shows the ring instead. */
.username-input:focus-within {
  outline: 2px solid var(--accent-text);
  outline-offset: 2px;
}

.username-input.is-disabled {
  opacity: 0.6;
}

.prefix {
  display: flex;
  align-items: center;
  padding: 0 0.7rem;
  font-size: 0.9rem;
  color: var(--text-muted);
  background: var(--surface-2);
  border-right: 1px solid var(--line);
  white-space: nowrap;
}

input {
  flex: 1;
  min-width: 0;
  font: inherit;
  font-size: 1rem;
  padding: 0.6rem 0.8rem;
  color: var(--text);
  background: transparent;
  border: none;
  outline: none;
}
</style>
