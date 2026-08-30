import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import SingleUserTab from './SingleUserTab.vue'

function jsonResponse(body, ok = true) {
  return Promise.resolve({
    ok,
    json: () => Promise.resolve(body)
  })
}

describe('SingleUserTab', () => {
  let existsResponses
  let watchlistImpl

  beforeEach(() => {
    try {
      localStorage.clear()
    } catch (e) {
      // no localStorage in this environment
    }
    existsResponses = {}
    watchlistImpl = () => Promise.reject(new Error('watchlist not mocked in this test'))

    global.fetch = vi.fn((url) => {
      const existsMatch = url.match(/^\/api\/users\/([^/]+)\/exists$/)
      if (existsMatch) {
        const username = decodeURIComponent(existsMatch[1])
        const result = existsResponses[username] ?? { exists: true, watchlistPublic: true }
        return jsonResponse(result)
      }
      if (url.startsWith('/api/streaming-providers')) {
        return jsonResponse([])
      }
      return watchlistImpl(url)
    })

    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  async function setUsername(wrapper, username) {
    await wrapper.find('input[type="text"]').setValue(username)
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
  }

  it('disables both search buttons until the username is filled in and verified', async () => {
    const wrapper = mount(SingleUserTab)
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.all-matches-button').attributes('disabled')).toBeDefined()

    await setUsername(wrapper, 'alice')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('.all-matches-button').attributes('disabled')).toBeUndefined()
  })

  it('shows an error and keeps the buttons disabled when the username does not exist', async () => {
    existsResponses = { ghost: { exists: false, watchlistPublic: false } }

    const wrapper = mount(SingleUserTab)
    await setUsername(wrapper, 'ghost')

    expect(wrapper.text()).toContain("This username doesn't exist on Letterboxd.")
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('shows a different error when the watchlist is private', async () => {
    existsResponses = { alice: { exists: true, watchlistPublic: false } }

    const wrapper = mount(SingleUserTab)
    await setUsername(wrapper, 'alice')

    expect(wrapper.text()).toContain("This user's watchlist isn't public, or is empty.")
    expect(wrapper.text()).not.toContain("doesn't exist")
  })

  it('seats the avatar on the armchair once the username is verified', async () => {
    existsResponses = {
      alice: { exists: true, watchlistPublic: true, avatarUrl: 'https://a.ltrbxd.com/resized/avatar/alice.jpg' }
    }

    const wrapper = mount(SingleUserTab)
    await setUsername(wrapper, 'alice')

    expect(wrapper.find('.seat').attributes('src')).toBe('https://a.ltrbxd.com/resized/avatar/alice.jpg')
  })

  it('does not seat anyone until the username is verified', () => {
    const wrapper = mount(SingleUserTab)

    expect(wrapper.findAll('.seat')).toHaveLength(0)
  })

  it('the primary action requests a single random pick from the watchlist endpoint', async () => {
    watchlistImpl = () =>
      jsonResponse([
        {
          title: 'Anora',
          url: 'https://letterboxd.com/film/anora/',
          rating: 4.1,
          length: 139,
          posterUrl: 'https://image.tmdb.org/t/p/w342/anora.jpg'
        }
      ])

    const wrapper = mount(SingleUserTab)
    await setUsername(wrapper, 'alice')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledWith('/api/watchlist?user=alice&random=true')
    expect(wrapper.find('.picked-title').text()).toBe('Anora')
    expect(wrapper.find('.picked-meta').text()).toBe('★ 4.1  ·  139 mins')
    expect(wrapper.find('.picked-poster').attributes('src')).toBe('https://image.tmdb.org/t/p/w342/anora.jpg')
    expect(wrapper.find('.results').exists()).toBe(false)
  })

  it('requests all films and renders the poster grid when the secondary button is clicked', async () => {
    watchlistImpl = () =>
      jsonResponse([
        {
          title: 'Anora',
          url: 'https://letterboxd.com/film/anora/',
          posterUrl: 'https://image.tmdb.org/t/p/w342/anora.jpg'
        }
      ])

    const wrapper = mount(SingleUserTab)
    await setUsername(wrapper, 'alice')
    await wrapper.find('.all-matches-button').trigger('click')
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledWith('/api/watchlist?user=alice')
    expect(wrapper.find('.results a').attributes('href')).toBe('https://letterboxd.com/film/anora/')
    expect(wrapper.find('.picked-film').exists()).toBe(false)
  })

  it('shows a no-films message when the watchlist is empty', async () => {
    watchlistImpl = () => jsonResponse([])

    const wrapper = mount(SingleUserTab)
    await setUsername(wrapper, 'alice')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('No films in this watchlist.')
  })

  it('shows the server error message when the watchlist is inaccessible', async () => {
    watchlistImpl = () => jsonResponse({ error: 'Watchlist inaccessible (private or nonexistent) for: ghost' }, false)

    const wrapper = mount(SingleUserTab)
    await setUsername(wrapper, 'alice')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Watchlist inaccessible (private or nonexistent) for: ghost')
  })

  it('shows a connection error when the request fails outright', async () => {
    watchlistImpl = () => Promise.reject(new Error('offline'))

    const wrapper = mount(SingleUserTab)
    await setUsername(wrapper, 'alice')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Could not reach the server. Please try again.')
  })

  it('downloads a CSV named after the single username', async () => {
    watchlistImpl = () =>
      jsonResponse([{ title: 'The Godfather (1972)', url: 'https://letterboxd.com/film/the-godfather/', year: 1972 }])

    const OriginalBlob = global.Blob
    let capturedParts = null
    global.Blob = vi.fn((parts, options) => {
      capturedParts = parts
      return new OriginalBlob(parts, options)
    })
    URL.createObjectURL = vi.fn(() => 'blob:mock-url')
    URL.revokeObjectURL = vi.fn()

    const originalCreateElement = document.createElement.bind(document)
    let createdAnchor = null
    vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      const el = originalCreateElement(tag)
      if (tag === 'a') createdAnchor = el
      return el
    })

    const wrapper = mount(SingleUserTab)
    await setUsername(wrapper, 'alice')
    await wrapper.find('.all-matches-button').trigger('click')
    await flushPromises()

    await wrapper.find('.download-button').trigger('click')

    expect(createdAnchor.download).toBe('alice_watchlist.csv')
    expect(capturedParts[0]).toBe('Title,Year\r\nThe Godfather,1972')

    global.Blob = OriginalBlob
  })
})
