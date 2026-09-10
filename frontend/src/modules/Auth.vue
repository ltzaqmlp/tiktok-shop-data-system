<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock, ArrowRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { save } from '../api/client'
import { passwordError } from '../api/format.mjs'
import { useAuth } from '../stores/auth'
import { clearSession } from '../router'
import logo from '../assets/brand/herbmoda-logo-dark.png'
const route = useRoute(), router = useRouter(), auth = useAuth(), form = ref<FormInstance>(), busy = ref(false), error = ref('')
const forced = computed(() => route.path === '/force-change-password')
const values = reactive({ username: '', password: '', oldPassword: '', newPassword: '', confirm: '', rememberMe: false })
const rules: FormRules = { username: [{ required: true, message: '请输入账号', trigger: 'blur' }], password: [{ required: true, message: '请输入密码', trigger: 'blur' }], oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }], newPassword: [{ validator: (_r, v, cb) => { const message = passwordError(v); cb(message ? new Error(message) : undefined) }, trigger: 'blur' }], confirm: [{ validator: (_r, v, cb) => cb(v && v === values.newPassword ? undefined : new Error('两次输入的密码不一致')), trigger: 'blur' }] }
async function submit() { if (!await form.value?.validate().catch(() => false)) return; busy.value = true; error.value = ''; try { if (forced.value) { await save('/auth/change-password', { oldPassword: values.oldPassword, newPassword: values.newPassword }); clearSession(); ElMessage.success('密码已更新，请重新登录'); await router.replace('/login') } else { await save('/auth/login', { username: values.username, password: values.password, rememberMe: values.rememberMe }); await auth.load(); const redirect = String(route.query.redirect ?? ''); const target = auth.allMenus.some(menu => menu.routePath === redirect) ? redirect : '/dashboard'; await router.replace(target) } } catch (e) { error.value = (e as Error).message } finally { busy.value = false; values.password = '' } }
</script>
<template>
    <main class="auth-page">
        <div class="auth-backdrop">
            <div class="backdrop-brand"><img :src="logo" alt="HERBMODA" /><span
                    class="wordmark">HERBMODA<small>LONDON</small></span></div>
            <div class="hero-media"><img class="oil" src="../assets/brand/radiant-oil-capsules-main.png" alt="" /><img
                    class="mask" src="../assets/brand/glowing-tomato-mask.png" alt="" /></div>
        </div>
        <div class="auth-card surface"><template v-if="!forced">
                <div class="auth-welcome"><span><strong style="font-size: 23px;">Welcome to</strong></span><strong
                        style="font-size: 15px;">HERBMODA Commerce
                        Intelligence</strong><small style="font-size: 10px;">Sign in to access your workspace</small>
                </div>
            </template><template v-else>
                <div class="auth-brand"><img :src="logo" alt="HERBMODA" /><span><strong>HERBMODA</strong><small>Commerce
                            Intelligence</small></span></div>
                <h1>设置您的新密码</h1>
                <p class="muted">首次登录需要修改临时密码，完成后即可使用系统。</p>
            </template><el-alert v-if="error" :title="error" type="error" show-icon :closable="false"
                role="alert" /><el-form ref="form" :model="values" :rules="rules" label-position="top"
                :class="{ 'password-change-form': forced }" @submit.prevent="submit"><template
                    v-if="!forced"><el-form-item label="Email or username" prop="username"><el-input
                            v-model="values.username" autocomplete="username" placeholder="Email or username"
                            size="large"><template #prefix><el-icon>
                                    <User />
                                </el-icon></template></el-input></el-form-item><el-form-item label="Password"
                        prop="password"><el-input v-model="values.password" type="password" show-password
                            autocomplete="current-password" placeholder="Password" size="large"><template
                                #prefix><el-icon>
                                    <Lock />
                                </el-icon></template></el-input></el-form-item>
                    <div class="login-options"><el-checkbox v-model="values.rememberMe">记住密码</el-checkbox>
                    </div><el-button class="submit" type="primary" native-type="submit" size="large"
                        :loading="busy">Sign in <el-icon>
                            <ArrowRight />
                        </el-icon></el-button>
                    <small class="auth-footnote">A smarter, more
                        radiant
                        tomorrow.</small>
                </template><template v-else><el-form-item label="原密码" prop="oldPassword"><el-input
                            v-model="values.oldPassword" type="password" show-password
                            autocomplete="current-password" /></el-form-item><el-form-item label="新密码"
                        prop="newPassword"><el-input v-model="values.newPassword" type="password" show-password
                            autocomplete="new-password" /><small class="muted">至少 10
                            位，包含字母、数字、符号中的两类</small></el-form-item><el-form-item label="确认新密码" prop="confirm"><el-input
                            v-model="values.confirm" type="password" show-password
                            autocomplete="new-password" /></el-form-item><el-button class="submit" type="primary"
                        native-type="submit" size="large" :loading="busy">保存并重新登录</el-button></template>
            </el-form></div>
    </main>
</template>
<style scoped>
.auth-page {
    min-height: 100vh;
    position: relative;
    isolation: isolate;
    overflow: hidden;
    background: #F4F8FC url('../assets/brand/herbmoda-login-background.png') center/cover no-repeat
}

.auth-page::before {
    content: '';
    position: absolute;
    inset: 0;
    background: url('../assets/brand/herbmoda-logo-light.webp') center 56%/clamp(340px, 39vw, 620px) auto no-repeat;
    opacity: .055;
    pointer-events: none;
    z-index: 0
}

.auth-backdrop {
    position: absolute;
    inset: 0;
    color: #17202B
}

.backdrop-brand {
    position: absolute;
    left: clamp(24px, 2.9vw, 88px);
    top: 12vh;
    display: flex;
    align-items: center;
    gap: 16px
}

.backdrop-brand img {
    width: 62px;
    height: 70px;
    object-fit: contain
}

.wordmark {
    font-family: Georgia, 'Times New Roman', serif;
    font-size: 32px;
    letter-spacing: .08em;
    line-height: .86
}

.wordmark small {
    display: block;
    text-align: center;
    font: 16px/1.3 Georgia, 'Times New Roman', serif;
    letter-spacing: .22em
}

.hero-media {
    display: none
}

.auth-card {
    position: absolute;
    z-index: 2;
    right: clamp(24px, 6.7vw, 134px);
    top: 50%;
    transform: translateY(-50%);
    width: min(19.5vw, 680px);
    min-width: 320px;
    padding: clamp(28px, 2.3vw, 48px);
    background: rgba(255, 255, 255, .82);
    border: 1px solid rgba(255, 255, 255, .96);
    border-radius: 22px;
    box-shadow: 0 18px 48px rgba(56, 80, 102, .13);
    backdrop-filter: blur(18px) saturate(115%)
}

.auth-welcome {
    display: grid;
    gap: 5px;
    margin-bottom: 36px;
    color: #111A25
}

.auth-welcome span {
    font-size: 16px
}

.auth-welcome strong {
    font-size: 18px;
    line-height: 1.15;
    white-space: nowrap;
    letter-spacing: -.02em
}

.auth-welcome small {
    color: #7F8D9D;
    font-size: 14px;
    margin-top: 3px
}

.auth-card :deep(.el-form-item) {
    margin-bottom: 18px
}

.auth-card :deep(.el-form-item__label) {
    display: none
}

.password-change-form :deep(.el-form-item__label) {
    display: block;
    padding-bottom: 6px;
    font-size: 14px;
    font-weight: 600;
    color: var(--hm-text)
}

.auth-card :deep(.el-input__wrapper) {
    min-height: 48px;
    border-radius: 9px;
    box-shadow: 0 0 0 1px #D9E1E8 inset !important;
    background: rgba(255, 255, 255, .54)
}

.auth-card :deep(.el-input__inner) {
    font-size: 14px;
    color: #516274
}

.auth-card :deep(.el-input__inner)::placeholder {
    color: #9BA8B6
}

.auth-card :deep(.el-input__prefix) {
    color: #7C8D9F;
    font-size: 19px
}

.login-options {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin: 4px 0 24px;
    color: #8090A1;
    font-size: 13px
}

.login-options :deep(.el-checkbox__label) {
    color: #8090A1;
    font-size: 13px;
    padding-left: 7px
}

.login-options :deep(.el-checkbox__inner) {
    border-color: #D6E0E9;
    border-radius: 4px
}

.submit {
    width: 100%;
    height: 48px;
    margin-top: 0;
    border-radius: 8px;
    font-size: 14px;
    background: #394755 !important;
    border-color: #394755 !important
}

.submit .el-icon {
    margin-left: 7px
}

.auth-footnote {
    display: block;
    margin-top: 34px;
    text-align: center;
    color: #9BA8B6;
    font-size: 12px
}

.auth-card :deep(.el-alert) {
    margin-bottom: 20px
}

.auth-brand {
    display: flex;
    align-items: center;
    gap: 10px;
    color: var(--hm-text);
    margin-bottom: 32px
}

.auth-brand img {
    width: 32px;
    height: 34px;
    object-fit: contain
}

.auth-brand strong {
    display: block;
    font-size: 16px;
    letter-spacing: .12em;
    font-weight: 650
}

.auth-brand small {
    display: block;
    margin-top: 2px;
    color: var(--hm-text-secondary);
    font-size: 9px;
    letter-spacing: .04em
}

.auth-card h1 {
    font-size: 22px;
    margin-bottom: 24px;
    font-weight: 650;
    color: var(--hm-text)
}

.auth-card p {
    font-size: 13px;
    line-height: 1.7;
    margin: -12px 0 20px
}

@media(max-width:1200px) {
    .auth-page {
        background-position: 8% center
    }

    .hero-media {
        left: 26%;
        width: 70%;
    }

    .auth-card {
        right: 3%;
        width: 340px;
        min-width: 0;
        padding: 32px
    }

    .backdrop-brand {
        left: 28px
    }

    .backdrop-brand img {
        width: 48px;
        height: 54px
    }

    .wordmark {
        font-size: 25px
    }

    .wordmark small {
        font-size: 13px
    }

}

@media(min-width:1201px) and (min-aspect-ratio:1/2.5) and (max-aspect-ratio:2.5/1) {
    .auth-page {
        background-position: 8% center
    }
}

@media(max-height:700px) and (min-width:768px) {
    .auth-card {
        top: 54.5%;
        padding: 28px 34px
    }

    .auth-welcome {
        margin-bottom: 24px
    }

    .auth-card :deep(.el-form-item) {
        margin-bottom: 14px
    }

    .login-options {
        margin: 0 0 18px
    }

    .auth-footnote {
        margin-top: 24px
    }
}

@media(max-width:767px) {
    .auth-page {
        min-height: 100svh
    }

    .auth-page::before {
        background-position: center 38%;
        background-size: min(78vw, 390px);
        opacity: .040
    }

    .auth-backdrop {
        position: relative;
        min-height: 100svh
    }

    .hero-media {
        left: -27%;
        top: 9%;
        width: 120%;
        height: 62%;
        opacity: .18
    }

    .hero-media .oil {
        width: 62%
    }

    .hero-media .mask {
        width: 52%;
    }

    .backdrop-brand {
        left: 22px;
        top: 26px;
        gap: 9px
    }

    .backdrop-brand img {
        width: 34px;
        height: 38px
    }

    .wordmark {
        font-size: 19px
    }

    .wordmark small {
        font-size: 9px
    }

    .auth-card {
        right: 20px;
        left: 20px;
        top: 52%;
        transform: translateY(-50%);
        width: auto;
        padding: 28px 24px;
        border-radius: 18px
    }

    .auth-welcome {
        margin-bottom: 28px
    }

    .auth-welcome strong {
        font-size: 19px;
        white-space: normal
    }

    .auth-welcome small {
        font-size: 12px
    }

    .login-options {
        font-size: 12px
    }

    .login-options :deep(.el-checkbox__label) {
        font-size: 12px
    }

    .auth-footnote {
        margin-top: 26px
    }
}
</style>
