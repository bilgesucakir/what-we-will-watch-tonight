import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { ref, nextTick } from 'vue'
import { useUsernameCheck, usernameFieldError } from './useUsernameCheck'

describe('usernameFieldError', () => {
  it('reports a nonexistent username', () => {
    expect(usernameFieldError(false, false)).toBe("This username doesn't exist on Letterboxd.")
  })

  it('reports an existing user with a non-public watchlist', () => {
    expect(usernameFieldError(true, false)).toBe("This user's watchlist isn't public, or is empty.")
  })

  it('returns null while the check is still pending or the user is usable', () => {
    expect(usernameFieldError(null, null)).toBeNull()
    expect(usernameFieldError(true, true)).toBeNull()
  })
})

describe('useUsernameCheck', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  async function settle() {
    await vi.advanceTimersByTimeAsync(500)
  }

  it('does not call the API for a blank value', async () => {
    global.fetch = vi.fn()
    const name = ref('  ')
    useUsernameCheck(name)
    await nextTick()
    await settle()

    expect(global.fetch).not.toHaveBeenCalled()
  })

  it('populates the refs from a successful check', async () => {
    global.fetch = vi.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ exists: true, watchlistPublic: true, avatarUrl: 'a.jpg' })
      })
    )
    const name = ref('')
    const check = useUsernameCheck(name)
    name.value = 'alice'
    await nextTick()
    await settle()

    expect(check.exists.value).toBe(true)
    expect(check.watchlistPublic.value).toBe(true)
    expect(check.avatarUrl.value).toBe('a.jpg')
  })

  it('treats a network failure as "not found" rather than throwing', async () => {
    global.fetch = vi.fn(() => Promise.reject(new Error('offline')))
    const name = ref('')
    const check = useUsernameCheck(name)
    name.value = 'alice'
    await nextTick()
    await settle()

    expect(check.exists.value).toBe(false)
    expect(check.watchlistPublic.value).toBe(false)
    expect(check.avatarUrl.value).toBeNull()
  })

  it('treats a non-OK response as "not found"', async () => {
    global.fetch = vi.fn(() => Promise.resolve({ ok: false, json: () => Promise.resolve({}) }))
    const name = ref('')
    const check = useUsernameCheck(name)
    name.value = 'alice'
    await nextTick()
    await settle()

    expect(check.exists.value).toBe(false)
  })

  it('ignores an in-flight response once the input has changed again', async () => {
    let resolveFetch
    global.fetch = vi.fn(() => new Promise((resolve) => (resolveFetch = resolve)))

    const name = ref('')
    const check = useUsernameCheck(name)
    name.value = 'alice'
    await nextTick()
    await vi.advanceTimersByTimeAsync(500) // debounce fires; the 'alice' fetch is now in flight

    name.value = 'alicia' // user keeps typing before it resolves
    await nextTick()

    resolveFetch({ ok: true, json: () => Promise.resolve({ exists: true, watchlistPublic: true }) })
    await flushPromises()

    // the stale 'alice' result must not land
    expect(check.exists.value).not.toBe(true)
  })
})
