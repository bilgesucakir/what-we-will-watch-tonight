import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import TwoPlusUserTab from './TwoPlusUserTab.vue'

function jsonResponse(body, ok = true) {
  return Promise.resolve({
    ok,
    json: () => Promise.resolve(body)
  })
}

describe('TwoPlusUserTab', () => {
  let existsResponses
  let intersectImpl
  let underwatchedImpl

  beforeEach(() => {
    existsResponses = {}
    intersectImpl = () => Promise.reject(new Error('intersect not mocked in this test'))
    underwatchedImpl = () =>
      jsonResponse({
        title: 'Wanda',
        url: 'https://letterboxd.com/film/wanda/',
        year: 1970,
        posterUrl: null
      })

    global.fetch = vi.fn((url) => {
      const existsMatch = url.match(/^\/api\/users\/([^/]+)\/exists$/)
      if (existsMatch) {
        const username = decodeURIComponent(existsMatch[1])
        const result = existsResponses[username] ?? { exists: true, watchlistPublic: true }
        return jsonResponse(result)
      }
      if (url === '/api/underwatched-pick') {
        return underwatchedImpl()
      }
      return intersectImpl(url)
    })

    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  async function setUsernames(wrapper, ...users) {
    while (wrapper.findAll('input').length < users.length) {
      await wrapper.find('.add-person').trigger('click')
    }
    const inputs = wrapper.findAll('input')
    for (let i = 0; i < users.length; i++) {
      await inputs[i].setValue(users[i])
    }
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
  }

  it('starts with two username inputs and no remove buttons', () => {
    const wrapper = mount(TwoPlusUserTab)

    expect(wrapper.findAll('input')).toHaveLength(2)
    expect(wrapper.findAll('.remove-person')).toHaveLength(0)
  })

  it('adds a third then a fourth person, capped at four', async () => {
    const wrapper = mount(TwoPlusUserTab)

    await wrapper.find('.add-person').trigger('click')
    expect(wrapper.findAll('input')).toHaveLength(3)

    await wrapper.find('.add-person').trigger('click')
    expect(wrapper.findAll('input')).toHaveLength(4)
    expect(wrapper.find('.add-person').exists()).toBe(false)
  })

  it('emits the group size so the sofa background can follow', async () => {
    const wrapper = mount(TwoPlusUserTab)
    expect(wrapper.emitted('sofa-count').at(-1)).toEqual([2])

    await wrapper.find('.add-person').trigger('click')
    expect(wrapper.emitted('sofa-count').at(-1)).toEqual([3])

    await wrapper.find('.remove-person').trigger('click')
    expect(wrapper.emitted('sofa-count').at(-1)).toEqual([2])
  })

  it('removing the third person shifts the fourth up into its place', async () => {
    const wrapper = mount(TwoPlusUserTab)
    await wrapper.find('.add-person').trigger('click')
    await wrapper.find('.add-person').trigger('click')

    const inputs = wrapper.findAll('input')
    await inputs[2].setValue('carol')
    await inputs[3].setValue('dave')

    await wrapper.findAll('.remove-person')[0].trigger('click')

    const remaining = wrapper.findAll('input')
    expect(remaining).toHaveLength(3)
    expect(remaining[2].element.value).toBe('dave')
  })

  it('disables both search buttons until every username is filled in and verified', async () => {
    const wrapper = mount(TwoPlusUserTab)
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()

    await setUsernames(wrapper, 'alice', 'bob')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('.all-matches-button').attributes('disabled')).toBeUndefined()

    await wrapper.find('.add-person').trigger('click')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('shows an error and keeps the buttons disabled when a username does not exist', async () => {
    existsResponses = { ghost: { exists: false, watchlistPublic: false } }

    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'ghost')

    expect(wrapper.text()).toContain("This username doesn't exist on Letterboxd.")
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('shows a different error when the watchlist is private', async () => {
    existsResponses = { bob: { exists: true, watchlistPublic: false } }

    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'bob')

    expect(wrapper.text()).toContain("This user's watchlist isn't public, or is empty.")
    expect(wrapper.text()).not.toContain("doesn't exist")
  })

  it('seats each verified user on a cushion', async () => {
    existsResponses = {
      alice: { exists: true, watchlistPublic: true, avatarUrl: 'https://a.ltrbxd.com/resized/avatar/alice.jpg' },
      bob: { exists: true, watchlistPublic: true, avatarUrl: 'https://a.ltrbxd.com/resized/avatar/bob.jpg' }
    }

    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'bob')

    const seats = wrapper.findAll('.seat')
    expect(seats).toHaveLength(2)
    expect(seats.map((s) => s.attributes('src'))).toEqual([
      'https://a.ltrbxd.com/resized/avatar/alice.jpg',
      'https://a.ltrbxd.com/resized/avatar/bob.jpg'
    ])
  })

  it('does not seat anyone whose check has no avatar url', async () => {
    existsResponses = { ghost: { exists: false, watchlistPublic: false, avatarUrl: null } }

    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'ghost')

    expect(wrapper.findAll('.seat')).toHaveLength(0)
  })

  it('debounces the existence check while typing', async () => {
    const wrapper = mount(TwoPlusUserTab)
    const input = wrapper.findAll('input')[0]

    await input.setValue('al')
    await vi.advanceTimersByTimeAsync(200)
    await input.setValue('alice')
    await vi.advanceTimersByTimeAsync(200)

    expect(global.fetch).not.toHaveBeenCalledWith(expect.stringMatching(/^\/api\/users\//))

    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledWith('/api/users/alice/exists')
    expect(global.fetch).not.toHaveBeenCalledWith('/api/users/al/exists')
  })

  it('does not call the intersect API when a username is missing', async () => {
    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', '')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(global.fetch).not.toHaveBeenCalledWith(expect.stringMatching(/^\/api\/intersect/))
  })

  it('allows typing the same username twice but rejects submitting it', async () => {
    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'alice')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Usernames must be different')
    expect(global.fetch).not.toHaveBeenCalledWith(expect.stringMatching(/^\/api\/intersect/))
  })

  it('the primary action requests a single random pick and shows it highlighted', async () => {
    intersectImpl = () =>
      jsonResponse([
        {
          title: 'Anora',
          url: 'https://letterboxd.com/film/anora/',
          posterUrl: 'https://image.tmdb.org/t/p/w342/anora.jpg'
        }
      ])

    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'bob')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledWith('/api/intersect?user=alice&user=bob&random=true')
    expect(wrapper.find('.picked-title').text()).toBe('Anora')
    expect(wrapper.find('.picked-poster').attributes('src')).toBe('https://image.tmdb.org/t/p/w342/anora.jpg')
    expect(wrapper.find('.results').exists()).toBe(false)
  })

  it('sends every username to the intersect API for a group of three', async () => {
    intersectImpl = () => jsonResponse([{ title: 'Anora', url: 'https://letterboxd.com/film/anora/', posterUrl: null }])

    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'bob', 'carol')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledWith('/api/intersect?user=alice&user=bob&user=carol&random=true')
  })

  it('requests all matches and renders the poster grid when the secondary button is clicked', async () => {
    intersectImpl = () =>
      jsonResponse([
        {
          title: 'Anora',
          url: 'https://letterboxd.com/film/anora/',
          posterUrl: 'https://image.tmdb.org/t/p/w342/anora.jpg'
        }
      ])

    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'bob')
    await wrapper.find('.all-matches-button').trigger('click')
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledWith('/api/intersect?user=alice&user=bob')
    expect(wrapper.find('.results a').attributes('href')).toBe('https://letterboxd.com/film/anora/')
    expect(wrapper.find('.picked-film').exists()).toBe(false)
  })

  it('downloads a CSV named after everyone in the group', async () => {
    intersectImpl = () =>
      jsonResponse([
        { title: 'The Godfather (1972)', url: 'https://letterboxd.com/film/the-godfather/', year: 1972 },
        { title: 'Parasite (2019)', url: 'https://letterboxd.com/film/parasite/', year: 2019 }
      ])

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

    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'bob', 'carol')
    await wrapper.find('.all-matches-button').trigger('click')
    await flushPromises()

    await wrapper.find('.download-button').trigger('click')

    expect(createdAnchor.download).toBe('alice_bob_carol_watchlist_intersection.csv')
    expect(capturedParts[0]).toBe('Title,Year\r\nThe Godfather,1972\r\nParasite,2019')

    global.Blob = OriginalBlob
  })

  it('shows the server error message when the API rejects the request', async () => {
    intersectImpl = () => jsonResponse({ error: "There's no Letterboxd user named 'bob'." }, false)

    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'bob')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain("There's no Letterboxd user named 'bob'.")
  })

  it('shows a connection error when the request fails outright', async () => {
    intersectImpl = () => Promise.reject(new Error('offline'))

    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'bob')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Could not reach the server. Please try again.')
  })

  it('recommends an underwatched film when the watchlists have nothing in common', async () => {
    intersectImpl = () => jsonResponse([])
    underwatchedImpl = () =>
      jsonResponse({
        title: 'Wanda',
        url: 'https://letterboxd.com/film/wanda/',
        year: 1970,
        posterUrl: 'https://image.tmdb.org/t/p/w342/wanda.jpg'
      })

    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'bob')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(global.fetch).toHaveBeenCalledWith('/api/underwatched-pick')
    expect(wrapper.text()).toContain('I bet none of you have seen this')
    expect(wrapper.find('.picked-title').text()).toBe('Wanda')
    expect(wrapper.find('.picked-poster').attributes('src')).toBe('https://image.tmdb.org/t/p/w342/wanda.jpg')
  })

  it('falls back to a plain no-matches message when there is no underwatched pick', async () => {
    intersectImpl = () => jsonResponse([])
    underwatchedImpl = () => Promise.resolve({ ok: false, status: 204, json: () => Promise.resolve(null) })

    const wrapper = mount(TwoPlusUserTab)
    await setUsernames(wrapper, 'alice', 'bob')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('No films in common.')
  })
})
