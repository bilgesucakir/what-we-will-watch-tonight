import { describe, it, expect, vi, afterEach } from 'vitest'
import { downloadFilmsAsCsv } from './csv'

function capture(films, filename = 'out.csv') {
  const OriginalBlob = global.Blob
  let parts = null
  global.Blob = vi.fn((p, options) => {
    parts = p
    return new OriginalBlob(p, options)
  })
  URL.createObjectURL = vi.fn(() => 'blob:mock')
  URL.revokeObjectURL = vi.fn()

  const realCreate = document.createElement.bind(document)
  let anchor = null
  vi.spyOn(document, 'createElement').mockImplementation((tag) => {
    const el = realCreate(tag)
    if (tag === 'a') anchor = el
    return el
  })

  downloadFilmsAsCsv(films, filename)

  global.Blob = OriginalBlob
  return { csv: parts[0], anchor }
}

describe('downloadFilmsAsCsv', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('strips a trailing "(year)" from the title and puts the year in its own column', () => {
    const { csv } = capture([{ title: 'Parasite (2019)', year: 2019 }])

    expect(csv).toBe('Title,Year\r\nParasite,2019')
  })

  it('leaves the year column blank when a film has no year', () => {
    const { csv } = capture([{ title: 'Some Short', year: null }])

    expect(csv).toBe('Title,Year\r\nSome Short,')
  })

  it('quotes fields that contain a comma or a quote, doubling inner quotes', () => {
    const { csv } = capture([
      { title: 'Goodbye, Dragon Inn', year: 2003 },
      { title: 'The "Burbs', year: 1989 }
    ])

    expect(csv).toContain('"Goodbye, Dragon Inn",2003')
    expect(csv).toContain('"The ""Burbs",1989')
  })

  it('names the download after the given filename and cleans up the object URL', () => {
    const { anchor } = capture([{ title: 'Wanda', year: 1970 }], 'alice_bob.csv')

    expect(anchor.download).toBe('alice_bob.csv')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock')
  })
})
