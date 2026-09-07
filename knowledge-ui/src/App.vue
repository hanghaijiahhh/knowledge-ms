<template>
  <div v-if="$route.path === '/login' || $route.path === '/register'">
    <router-view />
  </div>
  <el-container v-else style="height:100vh">
    <!-- 侧边栏 -->
    <el-aside width="220px" style="background:linear-gradient(180deg, #1f2d3d 0%, #304156 100%);color:#fff;overflow-y:auto">
      <div style="padding:24px 20px 16px;border-bottom:1px solid rgba(255,255,255,0.08)">
        <div style="font-size:20px;font-weight:700;letter-spacing:1px">📚 知识库平台</div>
        <div style="font-size:11px;color:#8b9db5;margin-top:4px">企业智能文档管理</div>
      </div>
      <el-menu :default-active="$route.path" background-color="transparent" text-color="#bfcbd9"
               active-text-color="#fff" router style="border-right:none;padding:8px 0">
        <el-menu-item index="/" style="margin:2px 12px;border-radius:8px;height:44px">
          <span>📄 文档管理</span>
        </el-menu-item>
        <el-menu-item index="/search" style="margin:2px 12px;border-radius:8px;height:44px">
          <span>🔍 搜索</span>
        </el-menu-item>
        <el-menu-item index="/agent" style="margin:2px 12px;border-radius:8px;height:44px">
          <span>🤖 AI 问答</span>
        </el-menu-item>
        <el-menu-item index="/audit" style="margin:2px 12px;border-radius:8px;height:44px">
          <span>📋 审计日志</span>
        </el-menu-item>
      </el-menu>
      <div style="position:absolute;bottom:24px;left:20px;right:20px">
        <div style="display:flex;align-items:center;gap:8px;padding:10px 12px;border-radius:8px;
                    background:rgba(255,255,255,0.06);cursor:pointer;font-size:13px;color:#bfcbd9"
             @click="logout">
          <span>🚪</span> <span>退出登录</span>
        </div>
      </div>
    </el-aside>

    <!-- 主内容 -->
    <el-container>
      <el-header style="height:48px;background:#fff;border-bottom:1px solid #e8e8e8;
                       display:flex;align-items:center;justify-content:space-between;padding:0 24px">
        <div style="font-size:13px;color:#666">
          <span style="color:#999">首页</span>
          <span style="margin:0 8px;color:#ccc">/</span>
          <span>{{ routeTitle }}</span>
        </div>
        <div style="font-size:13px;color:#999">
          👤 {{ username }}
        </div>
      </el-header>
      <el-main style="background:#f0f2f5;padding:20px 24px">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const username = computed(() => localStorage.getItem('username') || '用户')

const titleMap = {
  '/': '文档管理',
  '/search': '文档搜索',
  '/agent': 'AI 智能问答',
  '/audit': '审计日志'
}
const routeTitle = computed(() => titleMap[route.path] || '')

const logout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/login')
}
</script>

<style>
* { margin:0; padding:0; box-sizing:border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }

/* 侧边栏菜单激活态 */
.el-menu-item.is-active {
  background: rgba(64,158,255,0.2) !important;
  font-weight: 600;
}

/* 全局卡片圆角 */
.el-card {
  border-radius: 10px !important;
  border: 1px solid #ebeef5 !important;
}
.el-card__header {
  border-bottom: 1px solid #f0f0f0 !important;
  padding: 14px 20px !important;
  font-weight: 600;
}
.el-card__body {
  padding: 20px !important;
}

/* 按钮圆角 */
.el-button { border-radius: 6px !important; }
.el-button--primary { font-weight: 500; }

/* 输入框 */
.el-input__wrapper { border-radius: 6px !important; }

/* 表格 */
.el-table { border-radius: 8px; overflow:hidden; }
.el-table th.el-table__cell { background:#fafafa !important; }

/* dialog */
.el-dialog { border-radius: 12px !important; }
.el-dialog__header { padding: 20px 24px 0 !important; }
.el-dialog__body { padding: 16px 24px 24px !important; }

/* 分页器 */
.el-pagination { justify-content:flex-end; }
</style>
