import { describe, it, expect } from 'vitest'
import { formatRuntime, pickMeta } from './format'

describe('formatRuntime', () => {
  it('formats the runtime in minutes', () => {
    expect(formatRuntime(125)).toBe('125 mins')
  })

  it('returns null when the runtime is unknown', () => {
    expect(formatRuntime(null)).toBeNull()
    expect(formatRuntime(undefined)).toBeNull()
  })
})

describe('pickMeta', () => {
  it('joins the rating and runtime', () => {
    expect(pickMeta({ rating: 4.3, length: 125 })).toBe('★ 4.3  ·  125 mins')
  })

  it('shows just the rating when there is no runtime', () => {
    expect(pickMeta({ rating: 4.3, length: null })).toBe('★ 4.3')
  })

  it('shows just the runtime when there is no rating', () => {
    expect(pickMeta({ rating: null, length: 90 })).toBe('90 mins')
  })

  it('returns null when the film has neither', () => {
    expect(pickMeta({ rating: null, length: null })).toBeNull()
  })
})
