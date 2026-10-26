<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAccessMatrix,
  grantAccess,
  revokeAccess,
  type DatasourceAccessMatrix,
  type DatasourceAccessRow,
} from '@/api/admin'

/** Human labels for the grantable backend roles. */
const roleLabels: Record<string, string> = {
  student: '学生',
  counselor: '辅导员',
}

const loading = ref(false)
const matrix = ref<DatasourceAccessMatrix>({ roles: [], datasources: [] })

/** Loads the current matrix. */
async function load(): Promise<void> {
  loading.value = true
  try {
    matrix.value = await getAccessMatrix()
  } catch {
    ElMessage.error('加载数据源权限失败')
  } finally {
    loading.value = false
  }
}

/**
 * Grants or revokes one role on one datasource and applies the returned matrix.
 *
 * @param row the datasource row
 * @param role the role being toggled
 * @param granted the new checked state
 */
async function toggle(
  row: DatasourceAccessRow,
  role: string,
  granted: boolean,
): Promise<void> {
  try {
    matrix.value = granted
      ? await grantAccess(role, row.id)
      : await revokeAccess(role, row.id)
    ElMessage.success('已保存，下一次请求立即生效')
  } catch {
    ElMessage.error('保存失败')
    await load()
  }
}

onMounted(load)
</script>

<template>
  <el-card class="access" shadow="never">
    <template #header>
      <div class="access__header">
        <span>数据源权限</span>
        <el-button text @click="load">刷新</el-button>
      </div>
    </template>

    <el-alert
      class="access__hint"
      type="info"
      :closable="false"
      show-icon
      title="角色只能使用被授权的数据源；管理员不受限制。修改保存后对下一个请求立即生效。"
    />

    <el-table v-loading="loading" :data="matrix.datasources" class="access__table">
      <el-table-column prop="name" label="数据源" min-width="180" />
      <el-table-column prop="id" label="ID" min-width="120" />
      <el-table-column label="默认" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.defaultDatasource" size="small" type="success">默认</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="角色授权" min-width="220">
        <template #default="{ row }">
          <el-checkbox
            v-for="role in matrix.roles"
            :key="role"
            class="access__role"
            :model-value="row.grantedRoles.includes(role)"
            @change="(value: string | number | boolean) => toggle(row, role, value === true)"
          >
            {{ roleLabels[role] ?? role }}
          </el-checkbox>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.access__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.access__hint {
  margin-bottom: 16px;
}

.access__role {
  margin-right: 16px;
}
</style>
