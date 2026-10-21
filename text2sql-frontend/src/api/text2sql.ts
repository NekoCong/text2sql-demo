import { http } from './http'
import { readToken } from '@/utils/authStorage'
import type {
  ChartSpec,
  ConversationEvent,
  QueryResult,
  RunStep,
  SqlEvent,
  SseHandlers,
} from '@/types'

export { listDatasources } from './datasources'

/** Response of the synchronous text-to-SQL endpoint. */
export interface SyncQueryResponse {
  datasourceId: string
  dialect: string
  sql: string
  explanation: string | null
  result: QueryResult
}

/**
 * Calls the synchronous text-to-SQL endpoint.
 *
 * @param message natural-language question
 * @param datasourceId datasource to query
 * @param sessionId conversation session id
 * @returns the assembled answer including the result rows
 */
export async function queryText2Sql(
  message: string,
  datasourceId: string,
  sessionId: string,
): Promise<SyncQueryResponse> {
  const { data } = await http.post<SyncQueryResponse>('/text2sql/query', {
    message,
    datasourceId,
    sessionId,
  })
  return data
}

/**
 * Decodes an SSE event payload as JSON, falling back to the raw string when the payload
 * is not valid JSON (e.g. the plain-text `error` event).
 *
 * @param raw raw `data` string of the event
 * @returns the parsed payload, or the raw string
 */
function parseEventData<T>(raw: string): T | string {
  try {
    return JSON.parse(raw) as T
  } catch {
    return raw
  }
}

/**
 * Opens an SSE stream against the backend and forwards the pipeline events: the
 * conversation the exchange was persisted to, progress steps, the generated SQL and
 * the result rows, plus legacy text deltas.
 *
 * `EventSource` cannot attach request headers, so the JWT is appended to the URL as
 * the `access_token` query parameter accepted by the backend.
 *
 * @param message natural-language question
 * @param datasourceId datasource to query (omitted when empty)
 * @param conversationId conversation to append to (omitted when `null`; the backend then creates one)
 * @param handlers conversation / step / sql / rows / chart / delta / done / error callbacks
 * @returns a function that closes the stream
 */
export function streamText2Sql(
  message: string,
  datasourceId: string,
  conversationId: string | null,
  handlers: SseHandlers,
): () => void {
  const params = new URLSearchParams({ message })
  if (datasourceId) {
    params.set('datasourceId', datasourceId)
  }
  if (conversationId) {
    params.set('conversationId', conversationId)
  }
  const token = readToken()
  if (token) {
    params.set('access_token', token)
  }
  const source = new EventSource(`/api/text2sql/stream?${params.toString()}`)

  source.addEventListener('conversation', (event) => {
    const payload = parseEventData<ConversationEvent>((event as MessageEvent<string>).data)
    if (typeof payload !== 'string') {
      handlers.onConversation?.(payload)
    }
  })

  source.addEventListener('step', (event) => {
    const step = parseEventData<RunStep>((event as MessageEvent<string>).data)
    if (typeof step !== 'string') {
      handlers.onStep?.(step)
    }
  })

  source.addEventListener('sql', (event) => {
    const payload = parseEventData<SqlEvent>((event as MessageEvent<string>).data)
    if (typeof payload !== 'string') {
      handlers.onSql?.(payload)
    }
  })

  source.addEventListener('rows', (event) => {
    const result = parseEventData<QueryResult>((event as MessageEvent<string>).data)
    if (typeof result !== 'string') {
      handlers.onRows?.(result)
    }
  })

  source.addEventListener('chart', (event) => {
    const chart = parseEventData<ChartSpec>((event as MessageEvent<string>).data)
    if (typeof chart !== 'string') {
      handlers.onChart?.(chart)
    }
  })

  source.addEventListener('delta', (event) => {
    handlers.onDelta((event as MessageEvent<string>).data)
  })

  source.addEventListener('done', () => {
    handlers.onDone?.()
    source.close()
  })

  source.addEventListener('error', (event) => {
    // A payload means the backend emitted a named "error" event; no payload means the
    // connection dropped.
    const data = (event as MessageEvent<string>).data
    handlers.onError?.(data && data.length > 0 ? data : '连接已断开')
    source.close()
  })

  return () => source.close()
}
