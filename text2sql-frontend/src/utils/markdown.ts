/** A fenced code block extracted from an assistant answer. */
export interface CodeSegment {
  type: 'code'
  lang: string
  value: string
}

/** Plain text outside of code fences. */
export interface TextSegment {
  type: 'text'
  value: string
}

/** Ordered piece of a parsed assistant answer. */
export type Segment = CodeSegment | TextSegment

const FENCE = /```(\w*)\n([\s\S]*?)```/g

/**
 * Splits an assistant answer into text and fenced-code segments so SQL can be rendered
 * distinctly from prose.
 *
 * @param content raw assistant markdown
 * @returns the ordered segments
 */
export function parseSegments(content: string): Segment[] {
  const segments: Segment[] = []
  let lastIndex = 0
  let match: RegExpExecArray | null

  FENCE.lastIndex = 0
  while ((match = FENCE.exec(content)) !== null) {
    if (match.index > lastIndex) {
      segments.push({ type: 'text', value: content.slice(lastIndex, match.index) })
    }
    segments.push({
      type: 'code',
      lang: match[1] || 'sql',
      value: match[2].replace(/\n$/, ''),
    })
    lastIndex = match.index + match[0].length
  }

  if (lastIndex < content.length) {
    segments.push({ type: 'text', value: content.slice(lastIndex) })
  }

  return segments
}
