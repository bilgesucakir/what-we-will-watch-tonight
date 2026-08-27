import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import App from './App.vue'

describe('App', () => {
  beforeEach(() => {
    global.fetch = vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve({}) }))
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the two-user tab by default', () => {
    const wrapper = mount(App)

    expect(wrapper.text()).toContain("What We'll Watch Tonight")
    expect(wrapper.findAll('input')).toHaveLength(2)
  })

  it('switches to the single-user tab when clicked', async () => {
    const wrapper = mount(App)

    await wrapper.findAll('.tab')[1].trigger('click')

    expect(wrapper.text()).toContain("What I'll Watch Tonight")
    expect(wrapper.text()).not.toContain("What We'll Watch Tonight")
    expect(wrapper.findAll('input')).toHaveLength(1)
  })

  it('switches back to the two-user tab', async () => {
    const wrapper = mount(App)

    await wrapper.findAll('.tab')[1].trigger('click')
    await wrapper.findAll('.tab')[0].trigger('click')

    expect(wrapper.text()).toContain("What We'll Watch Tonight")
    expect(wrapper.findAll('input')).toHaveLength(2)
  })

  it('shows the GitHub link regardless of active tab', () => {
    const wrapper = mount(App)

    expect(wrapper.find('.github-link').attributes('href')).toBe(
      'https://github.com/bilgesucakir/what-we-will-watch-tonight'
    )
  })
})
