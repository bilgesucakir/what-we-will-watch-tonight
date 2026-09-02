import { ref, computed, watch } from 'vue'

// Countries with TMDB watch-provider coverage; the browser-detected one is
// preselected.
export const REGIONS = [
  { code: 'AR', name: 'Argentina' },
  { code: 'AU', name: 'Australia' },
  { code: 'AT', name: 'Austria' },
  { code: 'BE', name: 'Belgium' },
  { code: 'BR', name: 'Brazil' },
  { code: 'CA', name: 'Canada' },
  { code: 'CL', name: 'Chile' },
  { code: 'CZ', name: 'Czechia' },
  { code: 'DK', name: 'Denmark' },
  { code: 'FI', name: 'Finland' },
  { code: 'FR', name: 'France' },
  { code: 'DE', name: 'Germany' },
  { code: 'GR', name: 'Greece' },
  { code: 'HK', name: 'Hong Kong' },
  { code: 'IN', name: 'India' },
  { code: 'IE', name: 'Ireland' },
  { code: 'IL', name: 'Israel' },
  { code: 'IT', name: 'Italy' },
  { code: 'JP', name: 'Japan' },
  { code: 'MX', name: 'Mexico' },
  { code: 'NL', name: 'Netherlands' },
  { code: 'NZ', name: 'New Zealand' },
  { code: 'NO', name: 'Norway' },
  { code: 'PL', name: 'Poland' },
  { code: 'PT', name: 'Portugal' },
  { code: 'RO', name: 'Romania' },
  { code: 'RU', name: 'Russia' },
  { code: 'SG', name: 'Singapore' },
  { code: 'ZA', name: 'South Africa' },
  { code: 'KR', name: 'South Korea' },
  { code: 'ES', name: 'Spain' },
  { code: 'SE', name: 'Sweden' },
  { code: 'CH', name: 'Switzerland' },
  { code: 'TR', name: 'Türkiye' },
  { code: 'GB', name: 'United Kingdom' },
  { code: 'US', name: 'United States' }
]

const STORAGE_KEY = 'streamingFilter'

// Hand-picked provider order for Türkiye (TMDB's own ranking is poor there).
// Matched by name against TMDB's `provider_name`, case-insensitive.
export const CURATED_PROVIDERS = {
  TR: [
    'Netflix',
    'Amazon Prime Video',
    'Amazon Video',
    'Google Play Movies',
    'Disney Plus',
    'Apple TV Store',
    'puhutv',
    'MUBI',
    'TOD TV',
    'Crunchyroll',
    'YouTube Premium',
    'tabii',
    'HBO Max',
    'TV+'
  ]
}

// IANA timezone -> ISO-3166, covering REGIONS. Physical location beats UI
// language: an English browser in Istanbul still lands on TR.
const TIMEZONE_COUNTRY = {
  'America/Argentina/Buenos_Aires': 'AR',
  'Australia/Sydney': 'AU', 'Australia/Melbourne': 'AU', 'Australia/Brisbane': 'AU',
  'Australia/Perth': 'AU', 'Australia/Adelaide': 'AU',
  'Europe/Vienna': 'AT',
  'Europe/Brussels': 'BE',
  'America/Sao_Paulo': 'BR', 'America/Bahia': 'BR', 'America/Fortaleza': 'BR',
  'America/Toronto': 'CA', 'America/Vancouver': 'CA', 'America/Edmonton': 'CA',
  'America/Winnipeg': 'CA', 'America/Halifax': 'CA',
  'America/Santiago': 'CL',
  'Europe/Prague': 'CZ',
  'Europe/Copenhagen': 'DK',
  'Europe/Helsinki': 'FI',
  'Europe/Paris': 'FR',
  'Europe/Berlin': 'DE', 'Europe/Busingen': 'DE',
  'Europe/Athens': 'GR',
  'Asia/Hong_Kong': 'HK',
  'Asia/Kolkata': 'IN', 'Asia/Calcutta': 'IN',
  'Europe/Dublin': 'IE',
  'Asia/Jerusalem': 'IL', 'Asia/Tel_Aviv': 'IL',
  'Europe/Rome': 'IT',
  'Asia/Tokyo': 'JP',
  'America/Mexico_City': 'MX', 'America/Monterrey': 'MX', 'America/Tijuana': 'MX',
  'Europe/Amsterdam': 'NL',
  'Pacific/Auckland': 'NZ',
  'Europe/Oslo': 'NO',
  'Europe/Warsaw': 'PL',
  'Europe/Lisbon': 'PT',
  'Europe/Bucharest': 'RO',
  'Europe/Moscow': 'RU', 'Europe/Kaliningrad': 'RU', 'Asia/Yekaterinburg': 'RU',
  'Asia/Singapore': 'SG',
  'Africa/Johannesburg': 'ZA',
  'Asia/Seoul': 'KR',
  'Europe/Madrid': 'ES',
  'Europe/Stockholm': 'SE',
  'Europe/Zurich': 'CH',
  'Europe/Istanbul': 'TR',
  'Europe/London': 'GB',
  'America/New_York': 'US', 'America/Chicago': 'US', 'America/Denver': 'US',
  'America/Los_Angeles': 'US', 'America/Phoenix': 'US', 'America/Anchorage': 'US',
  'Pacific/Honolulu': 'US'
}

/**
 * Best guess at the user's country: browser timezone first, then the locale
 * region subtag ("en-GB" -> GB). Null when neither matches a region we offer.
 */
export function detectRegion() {
  const known = new Set(REGIONS.map((r) => r.code))

  try {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone
    if (tz && known.has(TIMEZONE_COUNTRY[tz])) return TIMEZONE_COUNTRY[tz]
  } catch (e) {
    // no Intl / timezone -- fall through to the locale
  }

  const locales =
    typeof navigator !== 'undefined' ? navigator.languages || [navigator.language] : []
  for (const locale of locales) {
    const region = locale?.split('-')[1]?.toUpperCase()
    if (region && known.has(region)) return region
  }

  return null
}

function loadStored() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) || {}
  } catch (e) {
    return {}
  }
}

function persist(state) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch (e) {
    // storage disabled / full -- the filter just won't be remembered
  }
}

/**
 * "Pick something streamable" state: region, picked services, on/off.
 * `enabled` always starts OFF and isn't persisted; region + services are.
 * The provider list is fetched lazily the first time the box is ticked.
 */
export function useStreamingFilter() {
  const stored = loadStored()

  const enabled = ref(false)
  // Null when the browser gives nothing usable; the UI then asks for a region.
  const region = ref(stored.region || detectRegion())
  const selectedIds = ref(Array.isArray(stored.providers) ? stored.providers : [])

  const providers = ref([])
  const loading = ref(false)

  async function loadProviders() {
    if (!region.value) {
      providers.value = []
      return
    }
    loading.value = true
    try {
      const response = await fetch(`/api/streaming-providers?region=${encodeURIComponent(region.value)}`)
      providers.value = response.ok ? await response.json() : []
    } catch (e) {
      providers.value = []
    } finally {
      loading.value = false
    }
  }

  function toggle(id) {
    selectedIds.value = selectedIds.value.includes(id)
      ? selectedIds.value.filter((x) => x !== id)
      : [...selectedIds.value, id]
  }

  function clear() {
    selectedIds.value = []
  }

  // Load the region's list, then drop any picked services it doesn't carry.
  async function refreshProviders() {
    await loadProviders()
    if (providers.value.length) {
      const available = new Set(providers.value.map((p) => p.id))
      selectedIds.value = selectedIds.value.filter((id) => available.has(id))
    }
  }

  // Fetch the list the first time the box is ticked (and never before).
  watch(enabled, (on) => {
    if (on && providers.value.length === 0) refreshProviders()
  })

  // Re-fetch on a region change if the filter is on; otherwise drop the stale
  // list so it's re-fetched on the next tick.
  watch(region, () => {
    if (enabled.value) refreshProviders()
    else providers.value = []
  })

  watch([region, selectedIds], () => {
    persist({ region: region.value, providers: selectedIds.value })
  }, { deep: true })

  // The filter only bites when it's on, a region is set, AND a service is picked.
  const active = computed(
    () => enabled.value && !!region.value && selectedIds.value.length > 0
  )

  const needsRegion = computed(() => !region.value)

  const selectedProviders = computed(() =>
    providers.value.filter((p) => selectedIds.value.includes(p.id))
  )

  // Query params to add to a random-pick request, or [] when the filter is off.
  function pickParams() {
    if (!active.value) return []
    return [
      ['region', region.value],
      ...selectedIds.value.map((id) => ['provider', String(id)])
    ]
  }

  return {
    enabled,
    region,
    providers,
    selectedIds,
    selectedProviders,
    loading,
    active,
    needsRegion,
    toggle,
    clear,
    pickParams
  }
}

/**
 * The line under the pick: the services the film is on, or a heads-up when
 * the filter was on and it's on none of the chosen ones.
 */
export function streamingNote(film, filter) {
  const on = film?.providers ?? []
  if (filter.active.value && !on.some((p) => filter.selectedIds.value.includes(p.id))) {
    return { warning: true, text: "Not on your streaming services — but it's the best we found." }
  }
  if (on.length === 0) return null
  return { warning: false, providers: on }
}
