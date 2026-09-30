import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import FilmGrid from './FilmGrid.vue'

function makeFilms(n) {
  return Array.from({ length: n }, (_, i) => ({
    title: `Film ${i + 1}`,
    url: `https://letterboxd.com/film/film-${i + 1}/`,
    year: 2000 + i,
    posterUrl: null
  }))
}

// Answers /api/posters with a poster for every film it's asked about.
function postersFor(body) {
  return JSON.parse(body).map((film) => ({ url: film.url, posterUrl: `${film.url}poster.jpg` }))
}

function requestedUrls(call) {
  return JSON.parse(call[1].body).map((film) => film.url)
}

describe('FilmGrid', () => {
  beforeEach(() => {
    global.fetch = vi.fn((url, init) =>
      Promise.resolve({ ok: true, json: () => Promise.resolve(postersFor(init.body)) })
    )
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the first 24 films and offers the rest', async () => {
    const wrapper = mount(FilmGrid, { props: { summary: 'x', films: makeFilms(30) } })
    await flushPromises()

    expect(wrapper.findAll('.results li')).toHaveLength(24)
    expect(wrapper.find('.show-more').text()).toBe('Show 6 more')
    expect(wrapper.find('.more-count').text()).toBe('Showing 24 of 30')
  })

  it('asks for posters for the shown page only, sending url, title and year', async () => {
    mount(FilmGrid, { props: { summary: 'x', films: makeFilms(30) } })
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledTimes(1)
    const [url, init] = global.fetch.mock.calls[0]
    expect(url).toBe('/api/posters')
    expect(init.method).toBe('POST')
    const sent = JSON.parse(init.body)
    expect(sent).toHaveLength(24)
    expect(sent[0]).toEqual({ url: 'https://letterboxd.com/film/film-1/', title: 'Film 1', year: 2000 })
  })

  it('shows the next page and fetches only its posters on "Show more"', async () => {
    const wrapper = mount(FilmGrid, { props: { summary: 'x', films: makeFilms(30) } })
    await flushPromises()

    await wrapper.find('.show-more').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.results li')).toHaveLength(30)
    expect(wrapper.find('.show-more').exists()).toBe(false)
    expect(global.fetch).toHaveBeenCalledTimes(2)
    expect(requestedUrls(global.fetch.mock.calls[1])).toEqual(
      makeFilms(30).slice(24).map((film) => film.url)
    )
  })

  it('has no "Show more" when everything fits on one page', async () => {
    const wrapper = mount(FilmGrid, { props: { summary: 'x', films: makeFilms(24) } })
    await flushPromises()

    expect(wrapper.findAll('.results li')).toHaveLength(24)
    expect(wrapper.find('.more').exists()).toBe(false)
  })

  it('shimmers while posters load, then shows them', async () => {
    let resolve
    global.fetch = vi.fn(
      (url, init) =>
        new Promise((r) => {
          resolve = () => r({ ok: true, json: () => Promise.resolve(postersFor(init.body)) })
        })
    )
    const wrapper = mount(FilmGrid, { props: { summary: 'x', films: makeFilms(2) } })
    await flushPromises()

    expect(wrapper.findAll('.poster-loading')).toHaveLength(2)

    resolve()
    await flushPromises()

    expect(wrapper.findAll('.poster-loading')).toHaveLength(0)
    expect(wrapper.find('img.poster').attributes('src')).toBe('https://letterboxd.com/film/film-1/poster.jpg')
  })

  it('shows the placeholder for a film with no poster', async () => {
    global.fetch = vi.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve([{ url: 'https://letterboxd.com/film/film-1/', posterUrl: null }])
      })
    )
    const wrapper = mount(FilmGrid, { props: { summary: 'x', films: makeFilms(1) } })
    await flushPromises()

    expect(wrapper.find('img.poster').exists()).toBe(false)
    expect(wrapper.find('.poster-placeholder').exists()).toBe(true)
  })

  it('falls back to placeholders when the poster request fails', async () => {
    global.fetch = vi.fn(() => Promise.reject(new Error('offline')))
    const wrapper = mount(FilmGrid, { props: { summary: 'x', films: makeFilms(3) } })
    await flushPromises()

    expect(wrapper.findAll('.poster-placeholder')).toHaveLength(3)
    expect(wrapper.findAll('.poster-loading')).toHaveLength(0)
  })

  it('goes back to the first page for a new list', async () => {
    const wrapper = mount(FilmGrid, { props: { summary: 'x', films: makeFilms(30) } })
    await flushPromises()
    await wrapper.find('.show-more').trigger('click')
    await flushPromises()

    await wrapper.setProps({ films: makeFilms(40) })
    await flushPromises()

    expect(wrapper.findAll('.results li')).toHaveLength(24)
    expect(wrapper.find('.more-count').text()).toBe('Showing 24 of 40')
  })

  it('links every film to its Letterboxd page', async () => {
    const wrapper = mount(FilmGrid, { props: { summary: 'x', films: makeFilms(1) } })
    await flushPromises()

    expect(wrapper.find('.results a').attributes('href')).toBe('https://letterboxd.com/film/film-1/')
    expect(wrapper.find('.poster-title').text()).toBe('Film 1')
  })

  it('shows the summary and asks the parent to download the CSV', async () => {
    const wrapper = mount(FilmGrid, { props: { summary: '30 films in common', films: makeFilms(30) } })
    await flushPromises()

    expect(wrapper.find('.list-summary').text()).toBe('30 films in common')
    await wrapper.find('.download-button').trigger('click')
    expect(wrapper.emitted('download')).toHaveLength(1)
  })
})
