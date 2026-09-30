import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PickCard from './PickCard.vue'

const film = {
  title: 'Anora',
  url: 'https://letterboxd.com/film/anora/',
  posterUrl: 'https://image.tmdb.org/t/p/w342/anora.jpg',
  rating: 4.1,
  length: 139
}

describe('PickCard', () => {
  it('shows the label, linked title and poster', () => {
    const wrapper = mount(PickCard, { props: { film, label: "Tonight's pick" } })
    expect(wrapper.find('.picked-label').text()).toBe("Tonight's pick")
    expect(wrapper.find('.picked-title').attributes('href')).toBe(film.url)
    expect(wrapper.find('.picked-poster').attributes('src')).toBe(film.posterUrl)
  })

  it('blurs the poster into the background glow', () => {
    const wrapper = mount(PickCard, { props: { film, label: 'x' } })
    expect(wrapper.find('.picked-glow').attributes('style')).toContain(film.posterUrl)
  })

  it('drops the glow and shows a placeholder when there is no poster', () => {
    const wrapper = mount(PickCard, { props: { film: { ...film, posterUrl: null }, label: 'x' } })
    expect(wrapper.find('.picked-glow').exists()).toBe(false)
    expect(wrapper.find('.poster-placeholder').exists()).toBe(true)
  })

  it('renders slot content under the details', () => {
    const wrapper = mount(PickCard, {
      props: { film, label: 'x' },
      slots: { default: '<p class="extra">Streaming on MUBI</p>' }
    })
    expect(wrapper.find('.picked-info .extra').text()).toBe('Streaming on MUBI')
  })
})
