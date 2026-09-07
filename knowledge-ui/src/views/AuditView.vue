<template>
  <div>
    <el-card shadow="never">
      <div style="display:flex;gap:10px;margin-bottom:20px;align-items:center;flex-wrap:wrap">
        <el-select v-model="moduleFilter" placeholder="全部模块" style="width:130px" clearable size="default">
          <el-option label="文档" value="doc" />
          <el-option label="文件" value="file" />
          <el-option label="认证" value="auth" />
        </el-select>
        <el-select v-model="actionFilter" placeholder="全部操作" style="width:130px" clearable size="default">
          <el-option label="创建" value="CREATE" />
          <el-option label="更新" value="UPDATE" />
          <el-option label="删除" value="DELETE" />
          <el-option label="登录" value="LOGIN" />
        </el-select>
        <el-button type="primary" @click="loadAudit">查询</el-button>
        <span style="font-size:13px;color:#999;margin-left:8px">共 {{ total }} 条</span>
      </div>

      <el-table :data="logs" stripe v-loading="loading" style="width:100%"
                :header-cell-style="{background:'#fafafa',color:'#333',fontWeight:600}">
        <el-table-column prop="id" label="#" width="70" align="center" />
        <el-table-column prop="username" label="操作用户" width="110" align="center" />
        <el-table-column prop="module" label="模块" width="100" align="center">
          <template #default="{row}">
            <el-tag size="small" effect="plain" type="info">{{ row.module }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作" width="100" align="center">
          <template #default="{row}">
            <el-tag size="small" :type="actionType(row.action)" effect="plain">
              {{ row.action }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="目标ID" width="90" align="center" />
        <el-table-column prop="targetName" label="目标名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="操作时间" width="175" align="center"
                         :formatter="fmtDate" />
      </el-table>

      <div style="margin-top:16px;display:flex;justify-content:space-between;align-items:center">
        <span style="font-size:13px;color:#999">共 {{ total }} 条记录</span>
        <el-pagination background layout="prev,next,sizes,total" :total="total"
                       v-model:page-size="pageSize" v-model:current-page="pageNum"
                       :page-sizes="[10,20,50]" @change="loadAudit" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import http from '../api/request.js'

const logs = ref([])
const loading = ref(false)
const moduleFilter = ref('')
const actionFilter = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const loadAudit = async () => {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (moduleFilter.value) params.module = moduleFilter.value
    if (actionFilter.value) params.action = actionFilter.value
    const { data } = await http.get('/audit/page', { params })
    if (data && data.data) {
      logs.value = data.data.records || []
      total.value = data.data.total || 0
    }
  } catch (e) {
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const actionType = (action) => {
  const map = { CREATE: 'success', UPDATE: 'warning', DELETE: 'danger', LOGIN: 'info' }
  return map[action] || 'info'
}

const fmtDate = (_row, _col, val) => {
  if (!val) return ''
  return val.replace('T', ' ').substring(0, 19)
}

onMounted(loadAudit)
</script>
