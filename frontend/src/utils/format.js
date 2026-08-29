/**
 * Turn a runtime in minutes into "125 mins", or null when unknown.
 */
export function formatRuntime(minutes) {
  if (minutes == null) return null
  return `${minutes} mins`
}

/**
 * "★ 4.3  ·  125 mins" for a picked film, or null when it has neither.
 */
export function pickMeta(film) {
  const parts = []
  if (film.rating != null) parts.push(`★ ${film.rating}`)
  const runtime = formatRuntime(film.length)
  if (runtime) parts.push(runtime)
  return parts.length ? parts.join('  ·  ') : null
}
