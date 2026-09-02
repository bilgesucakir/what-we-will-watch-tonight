// Letterboxd's list import matches by title text, so the year goes in its own
// column, not baked into "Title (Year)".
function csvTitle(title) {
  return title.replace(/\s*\(\d{4}\)\s*$/, '')
}

function csvEscape(value) {
  const text = String(value ?? '')
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

export function downloadFilmsAsCsv(films, filename) {
  const rows = [['Title', 'Year'], ...films.map((film) => [csvTitle(film.title), film.year ?? ''])]
  const csvContent = rows.map((row) => row.map(csvEscape).join(',')).join('\r\n')

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
