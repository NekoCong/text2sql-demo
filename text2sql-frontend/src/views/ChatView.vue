<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { DocumentCopy, Plus, Promotion, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { conversationErrorMessage } from '@/api/conversations'
import { useChatStore } from '@/stores/chat'
import { useConversationStore } from '@/stores/conversation'
import ResultChart from '@/components/ResultChart.vue'
import type {
  ChartSpec,
  ChatMessage,
  ConversationEvent,
  HistoryMessage,
  QueryResult,
  ChatStep,
} from '@/types'
import { parseSegments } from '@/utils/markdown'

const chat = useChatStore()
const conversation = useConversationStore()
const draft = ref('')
const scrollRef = ref<HTMLElement | null>(null)
/** True while a stored conversation history is being fetched. */
const loadingHistory = ref(false)
/** Conversation the live stream attached; keeps its `activeId` watcher idle. */
const streamedConversationId = ref<string | null>(null)

const examples = [
  '1班和2班的数学平均分分别是多少',
  '我的各科成绩',
  '统计每个班的考勤异常次数',
]

const canSend = computed(() => draft.value.trim().length > 0 && !chat.streaming)

const selectedDatasource = computed(
  () => chat.datasources.find((item) => item.id === chat.datasourceId) ?? null,
)

onMounted(() => {
  void chat.loadDatasources()
  if (conversation.activeId && !chat.streaming) {
    void loadConversation(conversation.activeId)
  }
})

// Switching conversations swaps the visible messages: load the stored history, or
// clear the pane for a fresh conversation.
watch(
  () => conversation.activeId,
  (id) => {
    if (id === null) {
      chat.stop()
      chat.reset()
      return
    }
    if (chat.streaming && id === streamedConversationId.value) {
      // The running stream persisted this conversation; the live view is already correct.
      return
    }
    void loadConversation(id)
  },
)

/**
 * Maps a stored history message onto the live chat message shape.
 *
 * @param message persisted message
 * @returns the renderable chat message
 */
function toChatMessage(message: HistoryMessage): ChatMessage {
  const mapped: ChatMessage = {
    id: message.id,
    role: message.role,
    content: message.content,
  }
  if (message.sql) {
    mapped.sql = message.sql
  }
  if (message.rows && Array.isArray(message.rows.columns) && Array.isArray(message.rows.rows)) {
    mapped.rows = message.rows
  }
  if (message.chart && typeof message.chart.type === 'string') {
    mapped.chart = message.chart
  }
  return mapped
}

/**
 * Replaces the visible messages with the stored history of a conversation.
 *
 * @param id conversation to load
 */
async function loadConversation(id: string): Promise<void> {
  if (chat.streaming) {
    chat.stop()
  }
  loadingHistory.value = true
  try {
    const history = await conversation.messagesFor(id)
    if (conversation.activeId !== id) {
      return
    }
    chat.replaceMessages(history.map(toChatMessage))
  } catch (error) {
    ElMessage.error(conversationErrorMessage(error, '加载会话消息失败'))
  } finally {
    loadingHistory.value = false
  }
}

function onSend(): void {
  if (!canSend.value) {
    return
  }
  chat.send(draft.value, conversation.activeId, onConversationAttached)
  draft.value = ''
}

/**
 * Reflects the conversation the stream persisted to: selects it, surfaces it in the
 * sidebar immediately, then reconciles with the backend list.
 *
 * @param event payload of the `conversation` SSE event
 */
function onConversationAttached(event: ConversationEvent): void {
  streamedConversationId.value = event.id
  conversation.attach(event.id, event.title)
  void conversation.load().catch(() => {
    // The locally attached entry stays; the next refresh reconciles it.
  })
}

/** Starts a fresh conversation; it is persisted right away so the sidebar lists it. */
async function onNewConversation(): Promise<void> {
  if (chat.streaming) {
    chat.stop()
  }
  try {
    await conversation.create()
  } catch (error) {
    ElMessage.error(conversationErrorMessage(error, '新建对话失败'))
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    onSend()
  }
}

function useExample(text: string): void {
  draft.value = text
}

function segments(content: string) {
  return parseSegments(content)
}

/** Steps that carry a real detail line; empty-detail steps are not rendered. */
function visibleSteps(message: ChatMessage): ChatStep[] {
  return (message.steps ?? []).filter((step) => stepText(step).length > 0)
}

/** Readable label for a pipeline step or an agent tool step. */
function stepText(step: ChatStep): string {
  if (step.phase !== 'tool') {
    return step.detail
  }
  const name = step.tool || '工具'
  if (step.status === 'start') {
    return `调用 ${name}…`
  }
  if (step.state === 'success') {
    return `${name} 完成`
  }
  const suffix = step.reason ? `：${step.reason}` : ''
  return `${name} ${step.state ?? '结束'}${suffix}`
}

/** Structured messages (steps / SQL / rows) get the full bubble width. */
function isRich(message: ChatMessage): boolean {
  return Boolean(message.sql || message.rows || (message.steps?.length ?? 0) > 0)
}

/** Stringifies a result cell for display. */
function cellText(row: Record<string, unknown>, key: string): string {
  const value = row[key]
  return value === null || value === undefined ? '' : String(value)
}

/** True when the chart spec references columns that actually exist in the result set. */
function hasChartFields(rows: QueryResult, chart: ChartSpec): boolean {
  const names = new Set(rows.columns.map((column) => column.name))
  return Boolean(
    chart.xField &&
      chart.yField &&
      names.has(chart.xField) &&
      names.has(chart.yField),
  )
}

async function copySql(message: ChatMessage): Promise<void> {
  const sql = message.sql
  if (!sql) {
    return
  }
  try {
    await navigator.clipboard.writeText(sql)
    ElMessage.success('SQL 已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

watch(
  () =>
    chat.messages
      .map(
        (message) =>
          `${message.content.length}:${message.steps?.length ?? 0}:${message.sql?.length ?? 0}:${message.explanation?.length ?? 0}:${message.rows ? 'r' : ''}`,
      )
      .join(','),
  () => {
    void nextTick(() => {
      const el = scrollRef.value
      if (el) {
        el.scrollTop = el.scrollHeight
      }
    })
  },
)
</script>

<template>
  <div class="chat">
    <el-card class="chat__panel" shadow="never">
      <template #header>
        <div class="chat__header">
          <span class="chat__title">Text2SQL 对话</span>
          <div class="chat__header-actions">
            <el-switch
              v-model="chat.agentMode"
              inline-prompt
              active-text="Agent"
              inactive-text="流水线"
              :disabled="chat.streaming"
            />
            <el-select
              v-model="chat.datasourceId"
              class="chat__datasource"
              size="small"
              placeholder="选择数据源"
              :disabled="chat.datasources.length === 0"
            >
              <el-option
                v-for="datasource in chat.datasources"
                :key="datasource.id"
                :label="datasource.name"
                :value="datasource.id"
              >
                <span class="chat__datasource-option">
                  <span class="chat__datasource-name">{{ datasource.name }}</span>
                  <el-tag size="small" effect="plain" type="info">{{ datasource.dialect }}</el-tag>
                </span>
              </el-option>
              <template #label>
                <span v-if="selectedDatasource" class="chat__datasource-option">
                  <span class="chat__datasource-name">{{ selectedDatasource.name }}</span>
                  <el-tag size="small" effect="plain" type="info">
                    {{ selectedDatasource.dialect }}
                  </el-tag>
                </span>
              </template>
            </el-select>
            <el-button :icon="Plus" text @click="onNewConversation">新建对话</el-button>
          </div>
        </div>
      </template>

      <div ref="scrollRef" v-loading="loadingHistory" class="chat__messages">
        <el-empty
          v-if="chat.messages.length === 0"
          description="输入自然语言问题，自动生成 SQL"
        />
        <div
          v-for="message in chat.messages"
          :key="message.id"
          class="bubble"
          :class="`bubble--${message.role}`"
        >
          <div class="bubble__role">{{ message.role === 'user' ? '我' : 'Text2SqlAgent' }}</div>
          <div
            class="bubble__body"
            :class="{ 'bubble__body--error': message.error, 'bubble__body--rich': isRich(message) }"
          >
            <div
              v-if="visibleSteps(message).length > 0"
              class="steps"
              :class="{ 'steps--live': message.streaming }"
            >
              <div v-for="(step, index) in visibleSteps(message)" :key="index" class="steps__item">
                <span class="steps__dot" />
                <span class="steps__detail">{{ stepText(step) }}</span>
              </div>
            </div>

            <p v-if="message.explanation" class="bubble__explanation">{{ message.explanation }}</p>

            <template v-for="(segment, index) in segments(message.content)" :key="index">
              <pre v-if="segment.type === 'code'" class="bubble__code"><code>{{ segment.value }}</code></pre>
              <div v-else-if="segment.type === 'table'" class="md-table">
                <table>
                  <thead>
                    <tr>
                      <th v-for="(header, cellIndex) in segment.headers" :key="cellIndex">{{ header }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, rowIndex) in segment.rows" :key="rowIndex">
                      <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p v-else class="bubble__text">{{ segment.value }}</p>
            </template>

            <div v-if="message.sql" class="sql">
              <div class="sql__bar">
                <span class="sql__label">SQL</span>
                <el-tag v-if="message.dialect" size="small" effect="plain" type="info">
                  {{ message.dialect }}
                </el-tag>
                <el-tag v-if="message.scopeApplied" size="small" effect="plain" type="success">
                  已按当前数据范围过滤
                </el-tag>
                <el-tag v-if="message.rowCount !== undefined" size="small" effect="plain" type="info">
                  {{ message.rowCount }} 行
                </el-tag>
                <el-button
                  class="sql__copy"
                  :icon="DocumentCopy"
                  text
                  size="small"
                  @click="copySql(message)"
                >
                  复制
                </el-button>
              </div>
              <pre class="bubble__code"><code>{{ message.sql }}</code></pre>
            </div>

            <div v-if="message.rows" class="result">
              <ResultChart
                v-if="
                  message.chart &&
                  message.chart.type !== 'NONE' &&
                  hasChartFields(message.rows, message.chart)
                "
                class="result__chart"
                :rows="message.rows"
                :chart="message.chart"
              />
              <el-table :data="message.rows.rows" size="small" border max-height="320">
                <el-table-column
                  v-for="column in message.rows.columns"
                  :key="column.name"
                  :prop="column.name"
                  :label="column.name"
                  min-width="120"
                  show-overflow-tooltip
                >
                  <template #default="{ row }">{{ cellText(row, column.name) }}</template>
                </el-table-column>
              </el-table>
              <div class="result__footer">
                <span>{{ message.rows.rowCount }} 行 · {{ message.rows.elapsedMs }}ms</span>
                <el-tag v-if="message.rows.truncated" size="small" type="warning" effect="plain">
                  已截断
                </el-tag>
              </div>
            </div>

            <span v-if="message.streaming" class="bubble__cursor">▋</span>
          </div>
        </div>
      </div>

      <div class="chat__examples">
        <el-tag
          v-for="example in examples"
          :key="example"
          class="chat__example"
          effect="plain"
          @click="useExample(example)"
        >
          {{ example }}
        </el-tag>
      </div>

      <div class="chat__composer">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="例如：查询每个月的订单数量（Enter 发送，Shift+Enter 换行）"
          @keydown="onKeydown"
        />
        <div class="chat__actions">
          <el-button v-if="chat.streaming" :icon="VideoPlay" @click="chat.stop()">停止</el-button>
          <el-button type="primary" :icon="Promotion" :disabled="!canSend" @click="onSend">
            发送
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.chat {
  height: 100%;
}

.chat__panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat__panel :deep(.el-card__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  gap: 12px;
}

.chat__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat__title {
  font-weight: 600;
}

.chat__header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat__datasource {
  width: 220px;
}

.chat__datasource-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.chat__datasource-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat__messages {
  flex: 1;
  overflow-y: auto;
  padding-right: 8px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.bubble__role {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.bubble--user {
  align-items: flex-end;
  display: flex;
  flex-direction: column;
}

.bubble__body {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble__body--rich {
  max-width: 100%;
}

.bubble--user .bubble__body {
  background: var(--el-color-primary-light-9);
}

.bubble__body--error {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.bubble__text {
  margin: 0;
}

.md-table {
  margin: 8px 0;
  max-width: 100%;
  overflow-x: auto;
}

.md-table table {
  border-collapse: collapse;
  width: 100%;
  font-size: 13px;
}

.md-table th,
.md-table td {
  border: 1px solid var(--el-border-color-light);
  padding: 6px 10px;
  text-align: left;
  white-space: nowrap;
}

.md-table th {
  background: var(--el-fill-color-light);
  font-weight: 600;
}

.bubble__explanation {
  margin: 0 0 8px;
}

.bubble__code {
  margin: 8px 0;
  padding: 12px;
  border-radius: 8px;
  background: #1e1e2e;
  color: #cdd6f4;
  overflow-x: auto;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 13px;
}

.bubble__cursor {
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.steps {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.steps__item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.steps__dot {
  flex: none;
  width: 6px;
  height: 6px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--el-color-primary);
}

.steps--live .steps__item:last-child .steps__dot {
  animation: step-pulse 1.2s ease-in-out infinite;
}

@keyframes step-pulse {
  50% {
    opacity: 0.25;
  }
}

.sql {
  margin-top: 8px;
}

.sql__bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sql__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.sql__copy {
  margin-left: auto;
}

.sql .bubble__code {
  margin: 8px 0 0;
}

.result {
  margin-top: 8px;
  white-space: normal;
}

.result__chart {
  margin-bottom: 10px;
}

.result__footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chat__examples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chat__example {
  cursor: pointer;
}

.chat__composer {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
