import { describe, it, expect, vi } from 'vitest'
import { ref, computed } from 'vue'
import { mount } from '@vue/test-utils'
import StreamingFilter from './StreamingFilter.vue'

// A hand-rolled stand-in for the useStreamingFilter() return value, so the
// component can be tested without the composable's fetch / localStorage.
function fakeFilter(overrides = {}) {
  const enabled = ref(overrides.enabled ?? false)
  const selectedIds = ref(overrides.selectedIds ?? [])
  const region = ref(overrides.region === undefined ? 'US' : overrides.region)
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

  it('spells out that the filter only applies to the random pick', () => {
    const wrapper = mount(StreamingFilter, { props: { filter: fakeFilter({ enabled: true }) } })

    expect(wrapper.find('.streaming-scope').text()).toMatch(/random pick only/i)
  })

  it('clears the selection with the clear button', async () => {
    const filter = fakeFilter({ enabled: true, selectedIds: [8] })
    const wrapper = mount(StreamingFilter, { props: { filter } })

    await wrapper.find('.streaming-clear').trigger('click')
    expect(filter.clear).toHaveBeenCalled()
  })
})
