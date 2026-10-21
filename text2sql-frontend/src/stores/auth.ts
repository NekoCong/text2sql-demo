import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { isAxiosError } from 'axios'
import type { AuthResponse, AuthUser } from '@/types'
import { login as loginRequest, me, register as registerRequest } from '@/api/auth'
import type { LoginPayload, RegisterPayload } from '@/api/auth'
import { clearStoredAuth, readToken, readUser, writeAuth } from '@/utils/authStorage'

/**
 * Session state for the auth flow: the JWT, the current user and the localStorage
 * mirror that the router guard and the axios interceptors fall back on.
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(readToken())
  const user = ref<AuthUser | null>(readUser())

  /** True when a token is on record; the backend rejects it if it has expired. */
  const isAuthenticated = computed(() => token.value !== null)

  /** Applies a register/login response to the store and to localStorage. */
  function applySession(response: AuthResponse): void {
    token.value = response.token
    user.value = response.user
    writeAuth(response.token, response.user)
  }

  /**
   * Signs in and stores the issued session.
   *
   * @param payload username + password
   */
  async function login(payload: LoginPayload): Promise<void> {
    applySession(await loginRequest(payload))
  }

  /**
   * Registers a new account; the backend logs it in right away.
   *
   * @param payload username + password (+ optional display name)
   */
  async function register(payload: RegisterPayload): Promise<void> {
    applySession(await registerRequest(payload))
  }

  /** Clears the session from the store and from localStorage. */
  function logout(): void {
    token.value = null
    user.value = null
    clearStoredAuth()
  }

  /**
   * Rehydrates the session after a page reload. When only the JWT survived (the user
   * entry is missing or corrupted) the profile is fetched via `me()`.
   */
  async function restore(): Promise<void> {
    const storedToken = readToken()
    token.value = storedToken
    user.value = readUser()
    if (!storedToken || user.value) {
      return
    }
    try {
      const current = await me()
      user.value = current
      writeAuth(storedToken, current)
    } catch (error) {
      // Only an explicit 401 invalidates the session; an unreachable backend does not.
      if (isAxiosError(error) && error.response?.status === 401) {
        logout()
      }
    }
  }

  return {
    token,
    user,
    isAuthenticated,
    login,
    register,
    logout,
    restore,
  }
})
