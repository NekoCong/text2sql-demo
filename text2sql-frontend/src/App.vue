<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Expand, Fold, User } from '@element-plus/icons-vue'
import ConversationSidebar from '@/components/ConversationSidebar.vue'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useConversationStore } from '@/stores/conversation'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const chat = useChatStore()
const conversation = useConversationStore()

/** Public routes (login) render bare, without the app chrome. */
const showChrome = computed(() => route.meta.public !== true)

/** The history sidebar belongs to the chat screen. */
const showSidebar = computed(() => showChrome.value && route.name === 'chat')

/** Collapse state of the sidebar: 260px expanded, 0 collapsed. */
const collapsed = ref(false)
const asideWidth = computed(() => (collapsed.value ? '0px' : '260px'))

/** Header label: display name first, username as fallback. */
const userName = computed(() => auth.user?.displayName || auth.user?.username || '')

/** Account dropdown handler. */
async function onUserCommand(command: string | number | object): Promise<void> {
  if (command === 'logout') {
    auth.logout()
    await router.replace('/login')
    return
  }
  if (command === 'models') {
    await router.push('/settings/models')
  }
}

/** Selecting a conversation always returns to the chat screen. */
function onConversationSelect(): void {
  if (route.name !== 'chat') {
    void router.push('/')
  }
}

// Session lifecycle: load the history once signed in, drop it again on logout.
watch(
  () => auth.isAuthenticated,
  (authenticated) => {
    if (authenticated) {
      void conversation.load().catch(() => {
        // The sidebar stays empty; an expired token is handled by the http interceptor.
      })
      return
    }
    conversation.clear()
    chat.reset()
  },
  { immediate: true },
)
</script>

<template>
  <router-view v-if="!showChrome" />

  <el-container v-else class="layout">
    <el-header class="layout__header">
      <div class="layout__brand">Text2SQL</div>
      <el-button
        v-if="showSidebar"
        class="layout__collapse"
        text
        :icon="collapsed ? Expand : Fold"
        :title="collapsed ? '展开对话历史' : '收起对话历史'"
        @click="collapsed = !collapsed"
      />
      <el-menu
        :default-active="route.path"
        mode="horizontal"
        router
        :ellipsis="false"
        class="layout__menu"
      >
        <el-menu-item index="/">对话</el-menu-item>
        <el-menu-item index="/datasources">数据源</el-menu-item>
        <el-menu-item index="/about">关于</el-menu-item>
      </el-menu>

      <el-dropdown class="layout__user" trigger="click" @command="onUserCommand">
        <span class="layout__user-trigger">
          <el-icon class="layout__user-icon"><User /></el-icon>
          <span class="layout__user-name">{{ userName }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="models">模型配置</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-header>

    <el-container class="layout__body">
      <el-aside
        v-if="showSidebar"
        class="layout__aside"
        :class="{ 'layout__aside--collapsed': collapsed }"
        :width="asideWidth"
      >
        <ConversationSidebar @select="onConversationSelect" />
      </el-aside>

      <el-main class="layout__main">
        <router-view />
      </el-main>
    </el-container>

    <el-footer class="layout__footer">
      Text2SQL monorepo · Vue 3 + Element Plus · Spring Boot + AgentScope
    </el-footer>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.layout__header {
  display: flex;
  align-items: center;
  gap: 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.layout__brand {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.layout__collapse {
  color: var(--el-text-color-secondary);
}

.layout__menu {
  border-bottom: none;
}

.layout__user {
  margin-left: auto;
}

.layout__user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-primary);
  cursor: pointer;
  outline: none;
}

.layout__user-icon {
  color: var(--el-text-color-secondary);
}

.layout__user-name {
  font-size: 14px;
  transition: color 0.2s;
}

.layout__user-trigger:hover .layout__user-name {
  color: var(--el-color-primary);
}

.layout__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.layout__aside {
  overflow: hidden;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-light);
  transition: width 0.2s ease;
}

.layout__aside--collapsed {
  border-right: none;
}

.layout__main {
  display: flex;
  flex-direction: column;
  padding: 0;
  min-height: 0;
  background: var(--el-fill-color-lighter);
}

.layout__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border-top: 1px solid var(--el-border-color-light);
}
</style>
