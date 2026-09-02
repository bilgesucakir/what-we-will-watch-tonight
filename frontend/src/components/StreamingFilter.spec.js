import { describe, it, expect, vi } from 'vitest'
import { ref, computed } from 'vue'
import { mount } from '@vue/test-utils'
import StreamingFilter from './StreamingFilter.vue'

// A hand-rolled stand-in for the useStreamingFilter() return value, so the
// component can be tested without the composable's fetch / localStorage.
function fakeFilter(overrides = {}) {
  const enabled = ref(overrides.enabled ?? false)
  const selectedIds = ref(overrides.selectedIds ?? [])
  // Default to a region with no curated list, so the plain top-14 cap applies
  // unless a test opts into a curated one (TR / GB / US).
  const region = ref(overrides.region === undefined ? 'DE' : overrides.region)
  const providers = ref(
    overrides.providers ?? [
      { id: 8, name: 'Netflix', logoUrl: null },
      { id: 337, name: 'Disney Plus', logoUrl: null }
    ]
  )
  return {
    enabled,
    region,
    providers,
    selectedIds,
    loading: ref(false),
    active: computed(() => enabled.value && !!region.value && selectedIds.value.length > 0),
    needsRegion: computed(() => !region.value),
    toggle: vi.fn((id) => {
      selectedIds.value = selectedIds.value.includes(id)
        ? selectedIds.value.filter((x) => x !== id)
        : [...selectedIds.value, id]
    }),
    clear: vi.fn(() => {
      selectedIds.value = []
    })
  }
}

describe('StreamingFilter', () => {
  it('hides the region and services until the box is ticked', async () => {
    const filter = fakeFilter()
    const wrapper = mount(StreamingFilter, { props: { filter } })

    expect(wrapper.find('.streaming-body').exists()).toBe(false)

    await wrapper.find('.streaming-toggle input').setValue(true)
    expect(filter.enabled.value).toBe(true)
    expect(wrapper.find('.streaming-body').exists()).toBe(true)
    expect(wrapper.findAll('.chip')).toHaveLength(2)
  })

  it('unticking the box hides everything again', async () => {
    const wrapper = mount(StreamingFilter, {
      props: { filter: fakeFilter({ enabled: true, selectedIds: [8] }) }
    })

    expect(wrapper.find('.streaming-body').exists()).toBe(true)
    await wrapper.find('.streaming-toggle input').setValue(false)
    expect(wrapper.find('.streaming-body').exists()).toBe(false)
  })

  it('shows the body straight away when the filter is already enabled', () => {
    const wrapper = mount(StreamingFilter, {
      props: { filter: fakeFilter({ enabled: true }) }
    })

    expect(wrapper.find('.streaming-body').exists()).toBe(true)
  })

  it('toggles a provider on click', async () => {
    const filter = fakeFilter({ enabled: true })
    const wrapper = mount(StreamingFilter, { props: { filter } })

    const chips = wrapper.findAll('.chip')
    await chips[0].trigger('click')
    expect(filter.toggle).toHaveBeenCalledWith(8)
    expect(chips[0].classes()).toContain('chip--on')
  })

  it('asks the user to choose a country when none was detected', () => {
    const wrapper = mount(StreamingFilter, {
      props: { filter: fakeFilter({ enabled: true, region: null }) }
    })

    expect(wrapper.find('.streaming-hint').text()).toContain('pick a country')
    expect(wrapper.findAll('.chip')).toHaveLength(0)
  })

  it('clears the selection with the clear button', async () => {
    const filter = fakeFilter({ enabled: true, selectedIds: [8] })
    const wrapper = mount(StreamingFilter, { props: { filter } })

    await wrapper.find('.streaming-clear').trigger('click')
    expect(filter.clear).toHaveBeenCalled()
  })

  const manyProviders = (n) =>
    Array.from({ length: n }, (_, i) => ({ id: i + 1, name: `Service ${i + 1}`, logoUrl: null }))

  it('caps a long provider list at 14 and reveals the rest on demand', async () => {
    const filter = fakeFilter({ enabled: true, providers: manyProviders(20) })
    const wrapper = mount(StreamingFilter, { props: { filter } })

    expect(wrapper.findAll('.chip')).toHaveLength(14)
    const more = wrapper.find('.streaming-more')
    expect(more.text()).toBe('Show 6 more')

    await more.trigger('click')
    expect(wrapper.findAll('.chip')).toHaveLength(20)
    expect(wrapper.find('.streaming-more').text()).toBe('Show fewer')

    await wrapper.find('.streaming-more').trigger('click')
    expect(wrapper.findAll('.chip')).toHaveLength(14)
  })

  it('has no show-more button when the list fits', () => {
    const wrapper = mount(StreamingFilter, {
      props: { filter: fakeFilter({ enabled: true, providers: manyProviders(14) }) }
    })

    expect(wrapper.findAll('.chip')).toHaveLength(14)
    expect(wrapper.find('.streaming-more').exists()).toBe(false)
  })

  it('pins the curated services first for Türkiye and buries the rest', async () => {
    const providers = [
      { id: 1, name: 'Sun Nxt', logoUrl: null },
      { id: 2, name: 'Jolt Film', logoUrl: null },
      { id: 3, name: 'MUBI', logoUrl: null },
      { id: 4, name: 'Netflix', logoUrl: null },
      { id: 5, name: 'Curiosity Stream', logoUrl: null }
    ]
    const wrapper = mount(StreamingFilter, {
      props: { filter: fakeFilter({ enabled: true, region: 'TR', providers }) }
    })

    const names = wrapper.findAll('.chip').map((c) => c.text())
    expect(names).toEqual(['Netflix', 'MUBI'])
    expect(wrapper.find('.streaming-more').text()).toBe('Show 3 more')

    await wrapper.find('.streaming-more').trigger('click')
    expect(wrapper.findAll('.chip').map((c) => c.text())).toContain('Sun Nxt')
  })

  it('shows exactly the curated services for Türkiye, past the cap', () => {
    // Every CURATED_PROVIDERS.TR entry, plus filler that should stay hidden.
    const providers = [
      'Netflix', 'Amazon Prime Video', 'Amazon Video', 'Google Play Movies', 'Disney Plus',
      'Apple TV Store', 'puhutv', 'MUBI', 'TOD TV', 'Crunchyroll', 'YouTube Premium',
      'tabii', 'HBO Max', 'TV+', 'Some Junk Service', 'Another Junk Service'
    ].map((name, i) => ({ id: i + 1, name, logoUrl: null }))

    const wrapper = mount(StreamingFilter, {
      props: { filter: fakeFilter({ enabled: true, region: 'TR', providers }) }
    })

    // The curated list drives what shows, not COLLAPSED_LIMIT; the two junk
    // entries are behind the toggle.
    expect(wrapper.findAll('.chip')).toHaveLength(14)
    expect(wrapper.find('.streaming-more').text()).toBe('Show 2 more')
  })

  it('leaves other regions on TMDB order, no curation', () => {
    const providers = [
      { id: 1, name: 'Sky Go', logoUrl: null },
      { id: 2, name: 'Netflix', logoUrl: null },
      { id: 3, name: 'BBC iPlayer', logoUrl: null }
    ]
    const wrapper = mount(StreamingFilter, {
      props: { filter: fakeFilter({ enabled: true, region: 'GB', providers }) }
    })

    // Order untouched, nothing hidden.
    expect(wrapper.findAll('.chip').map((c) => c.text())).toEqual(['Sky Go', 'Netflix', 'BBC iPlayer'])
    expect(wrapper.find('.streaming-more').exists()).toBe(false)
  })

  it('keeps a ticked service from the hidden tail visible', () => {
    const wrapper = mount(StreamingFilter, {
      props: {
        filter: fakeFilter({ enabled: true, providers: manyProviders(20), selectedIds: [18] })
      }
    })

    const names = wrapper.findAll('.chip').map((c) => c.text())
    expect(names).toContain('Service 18')
    expect(wrapper.findAll('.chip')).toHaveLength(15)
    expect(wrapper.find('.streaming-more').text()).toBe('Show 5 more')
  })
})
