<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ChatDotRound, Delete, Edit, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { InputInstance } from 'element-plus'
import { conversationErrorMessage } from '@/api/conversations'
import { useConversationStore } from '@/stores/conversation'
import type { Conversation } from '@/types'

/**
 * Left-hand conversation history: grouped by date, with inline rename and a delete
 * confirmation. The component performs its own store calls so failures surface inline
 * as a message; the emitted events let the surrounding shell react (e.g. route back
 * to the chat screen when another page was open).
 */
const emit = defineEmits<{
  select: [id: string]
  create: []
  rename: [id: string, title: string]
  delete: [id: string]
}>()

const conversation = useConversationStore()

const renamingId = ref<string | null>(null)
const renameDraft = ref('')
const renameInput = ref<InputInstance | null>(null)

/** Binds the (single) inline rename input so it can be focused after opening. */
function setRenameInput(el: unknown): void {
  renameInput.value = (el as InputInstance | null) ?? null
}

/** Creates a conversation and selects it. */
async function onCreate(): Promise<void> {
  emit('create')
  try {
    await conversation.create()
  } catch (error) {
    ElMessage.error(conversationErrorMessage(error, '新建对话失败'))
  }
}

/** Selects a conversation unless its title is being edited. */
function onSelect(item: Conversation): void {
  if (renamingId.value === item.id) {
    return
  }
  emit('select', item.id)
  conversation.select(item.id)
}

/** Turns a row into an inline rename input. */
function startRename(item: Conversation): void {
  renamingId.value = item.id
  renameDraft.value = item.title
  void nextTick(() => {
    renameInput.value?.focus()
  })
}

/** Abandons the inline rename without saving. */
function cancelRename(): void {
  renamingId.value = null
  renameDraft.value = ''
}

/** Saves the inline rename; blank or unchanged titles are dropped. */
async function commitRename(item: Conversation): Promise<void> {
  if (renamingId.value !== item.id) {
    return
  }
  const title = renameDraft.value.trim()
  cancelRename()
  if (!title || title === item.title) {
    return
  }
  emit('rename', item.id, title)
  try {
    await conversation.rename(item.id, title)
  } catch (error) {
    ElMessage.error(conversationErrorMessage(error, '重命名失败'))
  }
}

/** Deletes a conversation after the inline confirmation. */
async function onDelete(item: Conversation): Promise<void> {
  emit('delete', item.id)
  try {
    await conversation.remove(item.id)
    ElMessage.success('对话已删除')
  } catch (error) {
    ElMessage.error(conversationErrorMessage(error, '删除对话失败'))
  }
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar__header">
      <span class="sidebar__title">对话历史</span>
    </div>

    <el-button class="sidebar__new" type="primary" :icon="Plus" @click="onCreate">
      新建对话
    </el-button>

    <div v-loading="conversation.loading" class="sidebar__list">
      <template v-for="group in conversation.groups" :key="group.label">
        <div class="sidebar__group">{{ group.label }}</div>
        <div
          v-for="item in group.items"
          :key="item.id"
          class="conv"
          :class="{ 'conv--active': item.id === conversation.activeId }"
          @click="onSelect(item)"
        >
          <el-icon class="conv__icon"><ChatDotRound /></el-icon>

          <el-input
            v-if="renamingId === item.id"
            :ref="setRenameInput"
            v-model="renameDraft"
            class="conv__input"
            size="small"
            maxlength="64"
            @click.stop
            @keydown.enter="commitRename(item)"
            @keydown.esc="cancelRename"
            @blur="commitRename(item)"
          />

          <template v-else>
            <span class="conv__title" :title="item.title">{{ item.title }}</span>
            <span class="conv__actions">
              <el-icon class="conv__action" title="重命名" @click.stop="startRename(item)">
                <Edit />
              </el-icon>
              <el-popconfirm
                :title="`确定删除对话「${item.title}」？`"
                width="220"
                confirm-button-text="删除"
                cancel-button-text="取消"
                confirm-button-type="danger"
                @confirm="onDelete(item)"
              >
                <template #reference>
                  <el-icon class="conv__action conv__action--danger" title="删除" @click.stop>
                    <Delete />
                  </el-icon>
                </template>
              </el-popconfirm>
            </span>
          </template>
        </div>
      </template>

      <div v-if="conversation.groups.length === 0" class="sidebar__empty">暂无历史对话</div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 260px;
  min-width: 260px;
  height: 100%;
  padding: 16px 12px;
}

.sidebar__title {
  font-size: 14px;
  font-weight: 600;
}

.sidebar__new {
  width: 100%;
}

.sidebar__list {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
}

.sidebar__group {
  padding: 12px 8px 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.sidebar__empty {
  padding: 32px 0;
  font-size: 12px;
  text-align: center;
  color: var(--el-text-color-placeholder);
}

.conv {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  color: var(--el-text-color-regular);
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.15s;
}

.conv:hover {
  background: var(--el-fill-color-light);
}

.conv--active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.conv__icon {
  flex: none;
  color: var(--el-text-color-secondary);
}

.conv--active .conv__icon {
  color: var(--el-color-primary);
}

.conv__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv__actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s;
}

.conv:hover .conv__actions,
.conv--active .conv__actions {
  opacity: 1;
  pointer-events: auto;
}

.conv__action {
  color: var(--el-text-color-secondary);
}

.conv__action:hover {
  color: var(--el-color-primary);
}

.conv__action--danger:hover {
  color: var(--el-color-danger);
}

.conv__input {
  flex: 1;
  min-width: 0;
}
</style>
