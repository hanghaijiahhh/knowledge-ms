<template>
  <div style="display:flex;justify-content:center;align-items:center;height:100vh;
              background:linear-gradient(135deg, #667eea 0%, #764ba2 100%)">
    <div style="width:420px">
      <div style="text-align:center;margin-bottom:28px">
        <div style="font-size:36px;margin-bottom:8px">📚</div>
        <div style="font-size:22px;font-weight:700;color:#fff;letter-spacing:2px">创建账号</div>
        <div style="font-size:12px;color:rgba(255,255,255,0.6);margin-top:6px">注册后即可使用知识库平台</div>
      </div>
      <el-card shadow="always" style="border-radius:12px">
        <template #header>
          <span style="font-weight:600;font-size:16px">账号注册</span>
        </template>
        <el-form :model="form" label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="用于登录" size="large" :prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" placeholder="至少6位" size="large"
                      :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="form.confirmPwd" type="password" placeholder="再次输入密码" size="large"
                      :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item label="真实姓名">
            <el-input v-model="form.realName" placeholder="如：张三" size="large" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="form.email" placeholder="可选，用于找回密码" size="large" />
          </el-form-item>
          <el-button type="primary" size="large" style="width:100%;margin-top:8px;font-weight:500"
                     @click="register" :loading="loading">
            注 册
          </el-button>
        </el-form>
        <div style="text-align:center;margin-top:16px;font-size:13px;color:#999">
          已有账号？<router-link to="/login" style="color:#409EFF">去登录</router-link>
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
const form = reactive({ username: '', password: '', confirmPwd: '', realName: '', email: '' })

const register = async () => {
  if (!form.username || !form.password) return
  if (form.password !== form.confirmPwd) {
    import('element-plus').then(m => m.ElMessage.warning('两次密码不一致'))
    return
  }
  loading.value = true
  try {
    await http.post('/auth/register', {
      username: form.username,
      password: form.password,
      realName: form.realName,
      email: form.email
    })
    import('element-plus').then(m => m.ElMessage.success('注册成功，请登录'))
    router.push('/login')
  } catch (e) {} finally { loading.value = false }
}
</script>
