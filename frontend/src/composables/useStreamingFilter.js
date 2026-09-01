import { ref, computed, watch } from 'vue'

// Countries TMDB has watch-provider coverage for. The user can switch between
// these; the one detected from the browser is preselected.
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

// Enough IANA-timezone -> ISO-3166 mappings to cover the REGIONS above; the
// timezone reflects where the user physically is, which beats the UI language
// (an English-language browser in Istanbul should still land on TR).
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
 * Best guess at the user's country from the browser: the IANA timezone first
 * (physical location), then the locale's region subtag ("en-GB" -> GB). Returns
 * null when neither points at a region we offer, so the caller can decide what
 * to do rather than being handed a wrong default.
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
 * Holds the "pick something streamable" state: which region, which
 * services the group subscribes to, and whether the filter is switched on.
 *
 * The filter always starts switched OFF -- `enabled` is never persisted, so a
 * fresh page load shows an unticked box and makes no network request. The
 * region's provider list is fetched lazily the first time the box is ticked.
 * Region + picked services ARE remembered, so ticking the box brings back the
 * previous choice.
 */
export function useStreamingFilter() {
  const stored = loadStored()

  const enabled = ref(false)
  // May be null when the browser gives us nothing usable -- the UI then asks
  // the user to pick a region before the filter can do anything.
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

  // Re-fetch on a region change while the filter is on; otherwise just drop
  // the now-stale list so it's re-fetched next time the box is ticked.
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
 * Given a picked film's `providers` and the filter state, the line to show
 * under the pick: the services it's on, or a heads-up when the filter was on
 * and it's on none of the chosen ones.
 */
export function streamingNote(film, filter) {
  const on = film?.providers ?? []
  if (filter.active.value && !on.some((p) => filter.selectedIds.value.includes(p.id))) {
    return { warning: true, text: "Not on your streaming services — but it's the best we found." }
  }
  if (on.length === 0) return null
  return { warning: false, providers: on }
}
