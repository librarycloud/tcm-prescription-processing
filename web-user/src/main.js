import { createApp } from 'vue';
import { createPinia } from 'pinia';
// 样式由 unplugin-vue-components 按需注入，无需全量引入
import './styles/theme.css';
import './styles/global.css';
import App from './App.vue';
import router from './router';
// 仅注册项目实际使用的 8 个图标，避免全量注册 250+ 个图标
import {
  Tickets,
  Expand,
  Fold,
  ArrowDown,
  Iphone,
  Lock,
  Refresh,
  Loading
} from '@element-plus/icons-vue';

const app = createApp(App);

const usedIcons = { Tickets, Expand, Fold, ArrowDown, Iphone, Lock, Refresh, Loading };
Object.entries(usedIcons).forEach(([key, component]) => {
  app.component(key, component);
});

app.use(createPinia());
app.use(router);
app.mount('#app');

