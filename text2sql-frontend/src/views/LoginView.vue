<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Avatar, Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { authErrorMessage } from '@/api/auth'
import type { RegisterPayload } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

interface LoginForm {
  username: string
  password: string
}

interface RegisterForm {
  username: string
  displayName: string
  password: string
  confirmPassword: string
}

const router = useRouter()
const auth = useAuthStore()

const activeTab = ref<'login' | 'register'>('login')
const loading = ref(false)

const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

const loginForm = reactive<LoginForm>({ username: '', password: '' })
const registerForm = reactive<RegisterForm>({
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
})

const loginRules: FormRules<LoginForm> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/** Confirm password is optional; when filled it must match the password. */
function validateConfirmPassword(
  _rule: unknown,
  value: unknown,
  callback: (error?: string | Error) => void,
): void {
  if (typeof value === 'string' && value.length > 0 && value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const registerRules: FormRules<RegisterForm> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }],
}

/** Signs in and lands on the chat page. */
async function submitLogin(): Promise<void> {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    await auth.login({ username: loginForm.username.trim(), password: loginForm.password })
    ElMessage.success('登录成功')
    await router.replace('/')
  } catch (error) {
    ElMessage.error(authErrorMessage(error, '登录失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}

/** Registers an account; the backend signs it in right away. */
async function submitRegister(): Promise<void> {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    const payload: RegisterPayload = {
      username: registerForm.username.trim(),
      password: registerForm.password,
    }
    const displayName = registerForm.displayName.trim()
    if (displayName) {
      payload.displayName = displayName
    }
    await auth.register(payload)
    ElMessage.success('注册成功，已自动登录')
    await router.replace('/')
  } catch (error) {
    ElMessage.error(authErrorMessage(error, '注册失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <el-card class="login__card" shadow="always">
      <div class="login__brand">
        <div class="login__logo">T2S</div>
        <div>
          <div class="login__name">Text2SQL</div>
          <div class="login__tagline">用自然语言对话你的数据</div>
        </div>
      </div>

      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            label-position="top"
            size="large"
            @submit.prevent="submitLogin"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="loginForm.username"
                maxlength="64"
                placeholder="请输入用户名"
                autocomplete="username"
              >
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                show-password
                placeholder="请输入密码"
                autocomplete="current-password"
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-button
              class="login__submit"
              type="primary"
              size="large"
              :loading="loading"
              @click="submitLogin"
            >
              登录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-position="top"
            size="large"
            @submit.prevent="submitRegister"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="registerForm.username"
                maxlength="64"
                placeholder="用于登录，例如 alice"
                autocomplete="username"
              >
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="显示名称（可选）" prop="displayName">
              <el-input
                v-model="registerForm.displayName"
                maxlength="64"
                placeholder="展示在页面右上角"
                autocomplete="nickname"
              >
                <template #prefix>
                  <el-icon><Avatar /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                show-password
                placeholder="请输入密码"
                autocomplete="new-password"
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="确认密码（可选）" prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                show-password
                placeholder="再次输入密码"
                autocomplete="new-password"
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-button
              class="login__submit"
              type="primary"
              size="large"
              :loading="loading"
              @click="submitRegister"
            >
              注册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background:
    radial-gradient(1100px 520px at 15% -10%, var(--el-color-primary-light-9), transparent 60%),
    radial-gradient(900px 480px at 100% 110%, var(--el-color-primary-light-8), transparent 55%),
    var(--el-fill-color-lighter);
}

.login__card {
  width: 400px;
  max-width: 100%;
}

.login__card :deep(.el-card__body) {
  padding: 28px 28px 24px;
}

.login__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.login__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--el-border-radius-base);
  background: linear-gradient(135deg, var(--el-color-primary), var(--el-color-primary-dark-2));
  color: var(--el-color-white);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.4px;
}

.login__name {
  font-size: 16px;
  font-weight: 700;
}

.login__tagline {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.login__submit {
  width: 100%;
  margin-top: 4px;
}
</style>
