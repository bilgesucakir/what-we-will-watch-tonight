<script setup>
import { ref } from 'vue'
import SingleUserTab from './components/SingleUserTab.vue'
import TwoUserTab from './components/TwoUserTab.vue'

const activeTab = ref('single')
</script>

<template>
  <a
    class="github-link"
    href="https://github.com/bilgesucakir/what-we-will-watch-tonight"
    target="_blank"
    rel="noopener noreferrer"
  >
    <svg viewBox="0 0 16 16" width="20" height="20" fill="currentColor" aria-hidden="true">
      <path
        d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38
        0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13
        -.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66
        .07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15
        -.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0
        1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82
        1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01
        1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0 0 16 8c0-4.42-3.58-8-8-8z"
      />
    </svg>
    <span>GitHub</span>
  </a>

  <main class="page">
    <nav class="tabs">
      <button
        type="button"
        :class="['tab', { active: activeTab === 'single' }]"
        @click="activeTab = 'single'"
      >
        Just Me
      </button>
      <button
        type="button"
        :class="['tab', { active: activeTab === 'two' }]"
        @click="activeTab = 'two'"
      >
        Me &amp; a Friend
      </button>
    </nav>

    <SingleUserTab v-if="activeTab === 'single'" />
    <TwoUserTab v-else />
  </main>
</template>

<style scoped>
:global(body) {
  background: #121212;
  margin: 0;
}

.github-link {
  position: fixed;
  top: 1rem;
  right: 1rem;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: #e0e0e0;
  font-size: 0.9rem;
  text-decoration: none;
}

.github-link:hover {
  color: #4a8f63;
}

.page {
  max-width: 32rem;
  margin: 3rem auto;
  padding: 0 1.5rem;
  font-family: system-ui, sans-serif;
  color: #f0f0f0;
}

.tabs {
  display: flex;
  margin-bottom: 2rem;
  border-bottom: 1px solid #2e2e2e;
}

.tab {
  flex: 1;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  color: #999;
  font-size: 0.95rem;
  font-weight: 600;
  text-align: center;
  padding: 0.6rem 0.2rem;
  margin-bottom: -1px;
  cursor: pointer;
}

.tab:hover {
  color: #e0e0e0;
}

.tab.active {
  color: #4a8f63;
  border-bottom-color: #4a8f63;
}
</style>
