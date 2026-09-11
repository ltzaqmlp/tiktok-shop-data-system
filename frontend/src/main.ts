import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElDialog, ElDrawer } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import App from './App.vue'
import { router } from './router'
for (const component of [ElDialog, ElDrawer]) {
  const props = component.props as Record<string, { default?: unknown }>
  props.closeOnClickModal.default = false
  props.closeOnPressEscape.default = false
}
createApp(App).use(createPinia()).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
