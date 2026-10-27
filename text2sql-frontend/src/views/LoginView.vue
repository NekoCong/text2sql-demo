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
  if (loading.value) {
    return
  }
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
  if (loading.value) {
    return
  }
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
    <div class="login__card">
      <aside class="login__brand">
        <div class="login__brand-inner">
          <div class="login__brand-lockup">
            <span class="login__mark" aria-hidden="true">T2S</span>
            <span class="login__brand-name">Text2SQL</span>
          </div>

          <div class="login__brand-copy">
            <p class="login__brand-kicker">智慧校园数据问答</p>
            <p class="login__brand-title">让校园数据<br />开口说话</p>
            <p class="login__brand-text">
              成绩、考勤、选课与教学运行数据，用一句自然语言提问，即可生成可核对的 SQL 与结果。
            </p>
          </div>

          <p class="login__brand-foot">结果仅限当前账号的数据权限范围</p>
        </div>
      </aside>

      <section class="login__panel">
        <header class="login__panel-head">
          <h1 class="login__panel-title">开始数据对话</h1>
          <p class="login__panel-text">登录或注册后，即可用自然语言查询你权限范围内的校园数据。</p>
        </header>

        <el-tabs v-model="activeTab" stretch class="login__tabs">
          <el-tab-pane label="登录" name="login">
            <el-form
              ref="loginFormRef"
              class="login__form"
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
                native-type="submit"
                :loading="loading"
              >
                登录
              </el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form
              ref="registerFormRef"
              class="login__form"
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
                native-type="submit"
                :loading="loading"
              >
                注册
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* ---------- Stage -------------------------------------------------------- */

.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--ac-space-32) var(--ac-space-24);
  background:
    radial-gradient(1100px 520px at 12% -10%, var(--el-color-primary-light-9), transparent 62%),
    radial-gradient(900px 480px at 100% 110%, var(--el-color-primary-light-8), transparent 58%),
    var(--ac-canvas);
}

.login__card {
  display: grid;
  grid-template-columns: minmax(280px, 0.92fr) minmax(340px, 1fr);
  width: min(880px, 100%);
  overflow: hidden;
  background: var(--ac-surface);
  border: 1px solid var(--ac-border-soft);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-md);
}

/* ---------- Brand panel (CSS-only pattern) ------------------------------- */

.login__brand {
  position: relative;
  padding: var(--ac-space-40) var(--ac-space-32);
  overflow: hidden;
  background:
    radial-gradient(120% 90% at 100% 0%, var(--ac-brand-glow), transparent 58%),
    linear-gradient(160deg, transparent 34%, var(--ac-brand-shade) 100%),
    linear-gradient(150deg, var(--ac-primary) 0%, var(--ac-primary-pressed) 100%);
}

/* Hairline grid, faded out towards the bottom so the copy stays calm. */
.login__brand::before {
  position: absolute;
  inset: 0;
  content: '';
  background-image:
    linear-gradient(to right, var(--ac-brand-grid) 1px, transparent 1px),
    linear-gradient(to bottom, var(--ac-brand-grid) 1px, transparent 1px);
  background-size: 28px 28px;
  -webkit-mask-image: linear-gradient(158deg, #000, transparent 76%);
  mask-image: linear-gradient(158deg, #000, transparent 76%);
  pointer-events: none;
}

.login__brand-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: var(--ac-space-24);
  height: 100%;
}

.login__brand-lockup {
  display: flex;
  align-items: center;
  gap: var(--ac-space-12);
}

.login__mark {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: var(--ac-primary);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.02em;
  background: var(--el-color-white);
  border-radius: var(--ac-radius-sm);
  box-shadow: var(--ac-brand-mark-shadow);
}

.login__brand-name {
  color: var(--el-color-white);
  font-family: var(--ac-font-serif);
  font-size: var(--ac-text-title);
  font-weight: 600;
  letter-spacing: 0.01em;
}

.login__brand-copy {
  display: flex;
  flex-direction: column;
  gap: var(--ac-space-8);
}

.login__brand-kicker {
  margin: 0;
  color: var(--ac-brand-text);
  font-size: var(--ac-text-meta);
  letter-spacing: 0.08em;
}

.login__brand-title {
  margin: 0;
  color: var(--el-color-white);
  font-family: var(--ac-font-serif);
  font-size: var(--ac-text-hero);
  font-weight: 600;
  line-height: 1.35;
}

.login__brand-text {
  margin: 0;
  color: var(--ac-brand-text);
  font-size: var(--ac-text-body);
  line-height: var(--ac-line-height);
}

.login__brand-foot {
  margin: 0;
  padding-top: var(--ac-space-16);
  color: var(--ac-brand-text);
  font-size: var(--ac-text-meta);
  line-height: var(--ac-line-height);
  border-top: 1px solid var(--ac-brand-rule);
}

/* ---------- Form panel --------------------------------------------------- */

.login__panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 460px;
  padding: var(--ac-space-40) var(--ac-space-40) var(--ac-space-32);
  background: var(--ac-surface);
}

.login__panel-head {
  margin-bottom: var(--ac-space-16);
}

.login__panel-title {
  margin: 0;
  color: var(--ac-ink);
  font-family: var(--ac-font-serif);
  font-size: var(--ac-text-title);
  font-weight: 600;
  line-height: 1.3;
}

.login__panel-text {
  margin: var(--ac-space-4) 0 0;
  color: var(--ac-muted);
  font-size: var(--ac-text-meta);
  line-height: var(--ac-line-height);
}

.login__tabs :deep(.el-tabs__header) {
  margin-bottom: var(--ac-space-16);
}

.login__tabs :deep(.el-tabs__item) {
  height: 40px;
  font-size: var(--ac-text-body);
  line-height: 40px;
}

.login__form :deep(.el-form-item__label) {
  color: var(--ac-text);
  font-size: var(--ac-text-body);
  line-height: var(--ac-line-height-control);
}

/* Each item reserves the error line below its control, so a validation message
   never reflows the card or shifts the submit button. */
.login__form :deep(.el-form-item) {
  --el-form-item-margin-bottom: var(--ac-space-24);

  position: relative;
}

.login__form :deep(.el-form-item__error) {
  position: absolute;
  top: 100%;
  left: 0;
  width: 100%;
  padding-top: 2px;
  font-size: var(--ac-text-meta);
  line-height: 1.5;
}

.login__submit {
  width: 100%;
  margin-top: var(--ac-space-8);
}

/* ---------- Compact brand header (< 900px) ------------------------------- */

@media (max-width: 899px) {
  .login {
    align-items: flex-start;
    padding: var(--ac-space-24) var(--ac-space-16);
  }

  .login__card {
    grid-template-columns: minmax(0, 1fr);
    width: min(440px, 100%);
  }

  .login__brand {
    padding: var(--ac-space-16) var(--ac-space-20);
  }

  .login__brand-inner {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
    justify-content: flex-start;
    gap: var(--ac-space-4) var(--ac-space-12);
    height: auto;
  }

  /* Flatten the copy block so the eyebrow sits next to the lockup. */
  .login__brand-copy {
    display: contents;
  }

  .login__brand-title,
  .login__brand-text,
  .login__brand-foot {
    display: none;
  }

  .login__brand-kicker {
    margin-left: auto;
    font-size: var(--ac-text-meta);
    letter-spacing: 0;
    text-align: right;
  }

  .login__mark {
    width: 32px;
    height: 32px;
    font-size: 11px;
  }

  .login__brand-name {
    font-size: var(--ac-text-lead);
  }

  .login__panel {
    min-height: 0;
    padding: var(--ac-space-24) var(--ac-space-20);
  }
}
</style>
