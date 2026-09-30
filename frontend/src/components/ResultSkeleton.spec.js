import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ResultSkeleton from './ResultSkeleton.vue'

describe('ResultSkeleton', () => {
  it('is shaped like the pick card by default', () => {
    const wrapper = mount(ResultSkeleton)
    expect(wrapper.find('.skeleton-pick').exists()).toBe(true)
    expect(wrapper.find('.skeleton-grid').exists()).toBe(false)
  })

  it('is shaped like the poster grid for the full list', () => {
    const wrapper = mount(ResultSkeleton, { props: { kind: 'grid' } })
    expect(wrapper.findAll('.skeleton-tile').length).toBeGreaterThan(0)
    expect(wrapper.find('.skeleton-pick').exists()).toBe(false)
  })

  it('shows the status message under the placeholder', () => {
    const wrapper = mount(ResultSkeleton, { slots: { default: 'Scraping…' } })
    expect(wrapper.find('.skeleton-status').text()).toBe('Scraping…')
  })
})
