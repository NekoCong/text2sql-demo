<script setup lang="ts">
const backendStack = [
  'Java 25 · Spring Boot 4',
  'Spring WebFlux (REST + SSE)',
  'AgentScope 2.0 (HarnessAgent)',
  'DashScope / OpenAI 兼容模型',
]

const frontendStack = [
  'Vue 3 + TypeScript',
  'Vite',
  'Pinia + Vue Router',
  'Element Plus + Axios',
]
</script>

<template>
  <div class="about">
    <el-card shadow="never">
      <template #header>
        <span class="about__title">关于 Text2SQL</span>
      </template>

      <p class="about__lead">
        一个将自然语言转成 SQL 的 monorepo 示例：前端 Vue 3，后端 Java + AgentScope。
      </p>

      <div class="about__grid">
        <div>
          <h4>后端 · text2sql-backend</h4>
          <ul>
            <li v-for="item in backendStack" :key="item">{{ item }}</li>
          </ul>
        </div>
        <div>
          <h4>前端 · text2sql-frontend</h4>
          <ul>
            <li v-for="item in frontendStack" :key="item">{{ item }}</li>
          </ul>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.about__title {
  font-weight: 600;
}

.about__lead {
  margin-top: 0;
  color: var(--el-text-color-regular);
}

.about__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 24px;
}

.about__grid h4 {
  margin: 0 0 8px;
}

.about__grid ul {
  margin: 0;
  padding-left: 18px;
  color: var(--el-text-color-regular);
}
</style>
