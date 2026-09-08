<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { save } from '../api/client'
import { passwordError } from '../api/format.mjs'
import { useAuth } from '../stores/auth'
import { clearSession } from '../router'
import logo from '../assets/brand/herbmoda-logo-light.webp'
const route = useRoute(), router = useRouter(), auth = useAuth(), form = ref<FormInstance>(), busy = ref(false), error = ref('')
const forced = computed(() => route.path === '/force-change-password')
const values = reactive({ username:'', password:'', oldPassword:'', newPassword:'', confirm:'' })
const rules: FormRules = { username:[{required:true,message:'请输入账号',trigger:'blur'}],password:[{required:true,message:'请输入密码',trigger:'blur'}],oldPassword:[{required:true,message:'请输入原密码',trigger:'blur'}],newPassword:[{validator:(_r,v,cb)=>{const message=passwordError(v);cb(message ? new Error(message) : undefined)},trigger:'blur'}],confirm:[{validator:(_r,v,cb)=>cb(v && v===values.newPassword ? undefined : new Error('两次输入的密码不一致')),trigger:'blur'}] }
async function submit() { if (!await form.value?.validate().catch(()=>false)) return; busy.value=true;error.value='';try { if(forced.value){await save('/auth/change-password',{oldPassword:values.oldPassword,newPassword:values.newPassword});clearSession();ElMessage.success('密码已更新，请重新登录');await router.replace('/login')}else{await save('/auth/login',{username:values.username,password:values.password});await auth.load();const redirect=String(route.query.redirect??'');const target=auth.allMenus.some(menu=>menu.routePath===redirect)?redirect:'/dashboard';await router.replace(target)} } catch(e){error.value=(e as Error).message}finally{busy.value=false;values.password=''} }
</script>
<template><main class="auth-page"><div class="auth-card surface"><div class="auth-brand"><img :src="logo" alt="HERBMODA"/><span><strong>HERBMODA</strong><small>Commerce Intelligence</small></span></div><h1>{{ forced ? '设置您的新密码' : '账号登录' }}</h1><p v-if="forced" class="muted">首次登录需要修改临时密码，完成后即可使用系统。</p><el-alert v-if="error" :title="error" type="error" show-icon :closable="false" role="alert"/><el-form ref="form" :model="values" :rules="rules" label-position="top" @submit.prevent="submit"><template v-if="!forced"><el-form-item label="账号" prop="username"><el-input v-model="values.username" autocomplete="username" placeholder="请输入账号" size="large"/></el-form-item><el-form-item label="密码" prop="password"><el-input v-model="values.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" size="large"/></el-form-item></template><template v-else><el-form-item label="原密码" prop="oldPassword"><el-input v-model="values.oldPassword" type="password" show-password autocomplete="current-password"/></el-form-item><el-form-item label="新密码" prop="newPassword"><el-input v-model="values.newPassword" type="password" show-password autocomplete="new-password"/><small class="muted">至少 10 位，包含字母、数字、符号中的两类</small></el-form-item><el-form-item label="确认新密码" prop="confirm"><el-input v-model="values.confirm" type="password" show-password autocomplete="new-password"/></el-form-item></template><el-button class="submit" type="primary" native-type="submit" size="large" :loading="busy">{{ forced ? '保存并重新登录' : '登录' }}</el-button></el-form></div></main></template>
<style scoped>
.auth-page{min-height:100vh;display:grid;place-items:center;padding:32px clamp(28px,8vw,120px);position:relative;isolation:isolate;overflow:hidden;background:#F7F8F7}
.auth-page::before{content:'';position:absolute;z-index:-2;left:clamp(28px,7vw,112px);top:50%;transform:translateY(-50%);width:min(57vw,860px);height:min(76vh,760px);background:url('../assets/brand/radiant-oil-capsules-main.png') center/contain no-repeat;filter:saturate(.84) contrast(.98);opacity:.98;pointer-events:none}
.auth-page::after{content:'';position:absolute;z-index:-3;left:clamp(2px,2vw,30px);bottom:-7vh;width:min(34vw,460px);height:min(52vh,560px);background:url('../assets/brand/glowing-tomato-mask.png') center bottom/contain no-repeat;filter:grayscale(.14) saturate(.7);opacity:.26;pointer-events:none}
.auth-card{width:400px;max-width:100%;padding:36px;background:rgba(255,255,255,.985);border:1px solid #E5E9E6;border-radius:10px;box-shadow:0 8px 24px rgba(20,24,21,.045);justify-self:end;position:relative;z-index:1}
.auth-brand{display:flex;align-items:center;gap:10px;color:#171A18;margin-bottom:32px}.auth-brand img{width:32px;height:32px;object-fit:contain;filter:brightness(0)}.auth-brand strong{display:block;font-family:Georgia,'Times New Roman',serif;font-size:16px;letter-spacing:.12em;font-weight:600}.auth-brand small{display:block;margin-top:2px;color:#707872;font-size:9px;letter-spacing:.04em}
.auth-card h1{font-size:22px;margin-bottom:24px;font-weight:650;color:#171A18}.auth-card p{font-size:13px;line-height:1.7;margin:-12px 0 20px}.submit{width:100%;margin-top:12px}
.auth-page:has(.auth-card)::marker{display:none}
@media(min-width:1180px){.auth-card{margin-right:1vw}.auth-page::before{left:5vw;width:min(60vw,900px)}}
@media(max-width:900px){.auth-page{padding:24px;background:#F7F8F7}.auth-page::before{left:-16vw;width:76vw;opacity:.22}.auth-page::after{left:auto;right:-13vw;width:45vw;opacity:.10}.auth-card{justify-self:center}}
@media(max-width:767px){.auth-page{padding:20px}.auth-page::before{left:-28vw;width:110vw;height:72vh;opacity:.10}.auth-page::after{right:-30vw;bottom:-12vh;width:72vw;opacity:.055}.auth-card{padding:28px 24px;box-shadow:0 8px 24px rgba(20,24,21,.04)}.auth-brand{margin-bottom:26px}}
</style>
