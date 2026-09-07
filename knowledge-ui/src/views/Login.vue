<template>
  <div style="display:flex;justify-content:center;align-items:center;height:100vh;
              background:linear-gradient(135deg, #667eea 0%, #764ba2 100%)">
    <div style="width:400px">
      <div style="text-align:center;margin-bottom:28px">
        <div style="font-size:36px;margin-bottom:8px">📚</div>
        <div style="font-size:22px;font-weight:700;color:#fff;letter-spacing:2px">知识库平台</div>
        <div style="font-size:12px;color:rgba(255,255,255,0.6);margin-top:6px">企业智能文档管理系统</div>
      </div>
      <el-card shadow="always" style="border-radius:12px">
        <template #header>
          <span style="font-weight:600;font-size:16px">账号登录</span>
        </template>
        <el-form :model="form" label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" size="large"
                      :prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large"
                      :prefix-icon="Lock" @keyup.enter="login" show-password />
          </el-form-item>
          <el-button type="primary" size="large" style="width:100%;margin-top:8px;font-weight:500"
                     @click="login" :loading="loading">
            登 录
          </el-button>
        </el-form>
        <div style="margin-top:20px;padding-top:16px;border-top:1px solid #f0f0f0">
          <div style="font-size:12px;color:#bbb;text-align:center;margin-bottom:10px">测试账号</div>
          <div style="display:flex;gap:8px;justify-content:center">
            <el-tag style="cursor:pointer" effect="plain" size="small"
                    @click="form.username='admin';form.password='admin123'">
              admin / admin123
            </el-tag>
            <el-tag style="cursor:pointer" effect="plain" size="small" type="info"
                    @click="form.username='zhangsan';form.password='user123'">
              zhangsan / user123
            </el-tag>
          </div>
          <div style="text-align:center;margin-top:12px;font-size:13px;color:#999">
            没有账号？<router-link to="/register" style="color:#409EFF">立即注册</router-link>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import http from '../api/request.js'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

const login = async () => {
  loading.value = true
  try {
    const { data } = await http.post('/auth/login', form)
    localStorage.setItem('token', data.data.token)
    localStorage.setItem('username', form.username)
    router.push('/')
  } catch(e) {} finally { loading.value = false }
}
</script>
