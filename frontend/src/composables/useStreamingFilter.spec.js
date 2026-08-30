import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { useStreamingFilter, detectRegion, streamingNote } from './useStreamingFilter'

function jsonResponse(body, ok = true) {
  return Promise.resolve({ ok, json: () => Promise.resolve(body) })
}

function stubTimezone(timeZone) {
  vi.stubGlobal('Intl', {
    ...Intl,
    DateTimeFormat: () => ({ resolvedOptions: () => ({ timeZone }) })
  })
}

const NETFLIX = { id: 8, name: 'Netflix', logoUrl: null }
const DISNEY = { id: 337, name: 'Disney Plus', logoUrl: null }

describe('detectRegion', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reads the country from the browser timezone', () => {
    stubTimezone('Europe/Istanbul')
    expect(detectRegion()).toBe('TR')
  })

  it('falls back to the locale region when the timezone is not one we map', () => {
    stubTimezone('Pacific/Fakaofo')
    vi.stubGlobal('navigator', { languages: ['en-GB', 'en'] })
    expect(detectRegion()).toBe('GB')
  })

  it('returns null when neither the timezone nor the locale points at a region we offer', () => {
    stubTimezone('Pacific/Fakaofo')
    vi.stubGlobal('navigator', { languages: ['eo'] })
    expect(detectRegion()).toBeNull()
  })
})

describe('useStreamingFilter', () => {
  beforeEach(() => {
    try {
      localStorage.clear()
    } catch (e) {
      // no localStorage in this environment
    }
    stubTimezone('Europe/London') // -> GB
    global.fetch = vi.fn((url) => {
      if (url.includes('region=TR')) return jsonResponse([NETFLIX])
      return jsonResponse([NETFLIX, DISNEY])
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('starts switched off and fetches nothing until the box is ticked', async () => {
    const filter = useStreamingFilter()
    await flushPromises()

    expect(filter.enabled.value).toBe(false)
    expect(global.fetch).not.toHaveBeenCalled()
    expect(filter.providers.value).toHaveLength(0)
  })

  it('loads the region provider list the first time the box is ticked', async () => {
    const filter = useStreamingFilter()
    await flushPromises()

    filter.enabled.value = true
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('/api/streaming-providers'))
    expect(filter.providers.value).toHaveLength(2)
  })

  it('does not carry the on/off state to a fresh instance -- it starts off again', async () => {
    const first = useStreamingFilter()
    first.enabled.value = true
    await flushPromises()

    const second = useStreamingFilter()
    expect(second.enabled.value).toBe(false)
  })

  it('is only "active" when switched on and at least one service is picked', async () => {
    const filter = useStreamingFilter()
    await flushPromises()

    expect(filter.active.value).toBe(false)
    filter.toggle(8)
    expect(filter.active.value).toBe(false)
    filter.enabled.value = true
    expect(filter.active.value).toBe(true)
  })

  it('toggles a provider id in and out of the selection', async () => {
    const filter = useStreamingFilter()
    await flushPromises()

    filter.toggle(8)
    filter.toggle(337)
    expect(filter.selectedIds.value).toEqual([8, 337])
    filter.toggle(8)
    expect(filter.selectedIds.value).toEqual([337])
  })

  it('pickParams is empty when inactive and carries region + providers when active', async () => {
    const filter = useStreamingFilter()
    await flushPromises()

    expect(filter.pickParams()).toEqual([])

    filter.enabled.value = true
    filter.toggle(8)
    filter.toggle(337)
    expect(filter.pickParams()).toEqual([
      ['region', filter.region.value],
      ['provider', '8'],
      ['provider', '337']
    ])
  })

  it('drops selections the new region does not offer when the region changes', async () => {
    const filter = useStreamingFilter()
    filter.enabled.value = true
    await flushPromises()

    filter.toggle(8)
    filter.toggle(337)
    filter.region.value = 'TR'
    await flushPromises()

    expect(filter.selectedIds.value).toEqual([8])
  })
})

describe('streamingNote', () => {
  const activeFilter = { active: { value: true }, selectedIds: { value: [8] } }
  const inactiveFilter = { active: { value: false }, selectedIds: { value: [] } }

  it('warns when the filter was on but the pick is on none of the chosen services', () => {
    const note = streamingNote({ providers: [DISNEY] }, activeFilter)
    expect(note.warning).toBe(true)
  })

  it('lists the services the pick is on', () => {
    const note = streamingNote({ providers: [NETFLIX] }, activeFilter)
    expect(note).toEqual({ warning: false, providers: [NETFLIX] })
  })

  it('is null when the pick has no known services and the filter is off', () => {
    expect(streamingNote({ providers: [] }, inactiveFilter)).toBeNull()
  })
})
