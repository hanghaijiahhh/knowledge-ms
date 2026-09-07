<template>
  <div style="display:flex;gap:20px;align-items:flex-start">
    <!-- 左侧分类树 -->
    <div style="width:230px;flex-shrink:0">
      <el-card shadow="never">
        <template #header>
          <div style="display:flex;align-items:center;justify-content:space-between">
            <span>📁 文档分类</span>
            <span style="font-size:12px;color:#999">{{ categories.length }} 个</span>
          </div>
        </template>
        <div v-if="categoryIds.length" style="margin-bottom:8px;text-align:right">
          <el-button size="small" text @click="clearCategories">清除筛选</el-button>
        </div>
        <el-tree ref="treeRef" :data="categories" :props="{ label:'name', children:'children' }"
                 node-key="id" show-checkbox @check="onTreeCheck" @node-click="onNodeClick"
                 default-expand-all style="max-height:360px;overflow-y:auto" />
        <div style="margin-top:12px;padding-top:12px;border-top:1px solid #f0f0f0">
          <el-input v-model="newCatName" placeholder="新分类名称" size="small" />
          <div style="display:flex;align-items:center;justify-content:space-between;margin-top:6px">
            <span style="font-size:11px;color:#999">{{ selectedCatId ? '子分类' : '根级分类' }}</span>
            <el-button size="small" type="primary" @click="addCategory" :disabled="!newCatName">添加</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 右侧文档区 -->
    <div style="flex:1;min-width:0">
      <el-card shadow="never">
        <!-- 工具栏 -->
        <div style="display:flex;gap:10px;margin-bottom:20px;align-items:center;flex-wrap:wrap">
          <el-input v-model="keyword" placeholder="搜索文档标题..." style="width:220px"
                    @keyup.enter="loadDocs" clearable :prefix-icon="Search" />
          <el-select v-model="statusFilter" placeholder="全部状态" style="width:110px" clearable>
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="草稿" value="DRAFT" />
          </el-select>
          <el-button type="primary" @click="loadDocs" :icon="Search">查询</el-button>
          <div style="flex:1" />
          <el-button type="primary" @click="showCreate">+ 新建文档</el-button>
        </div>

        <!-- 表格 -->
        <el-table :data="docs" stripe v-loading="loading"
                  style="width:100%" :header-cell-style="{background:'#fafafa',color:'#333',fontWeight:600}">
          <el-table-column prop="id" label="#" width="60" align="center" />
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="creatorName" label="创建人" width="90" align="center" />
          <el-table-column prop="status" label="状态" width="85" align="center">
            <template #default="{row}">
              <el-tag :type="row.status==='PUBLISHED'?'success':'info'" size="small" effect="plain">
                {{ row.status==='PUBLISHED' ? '已发布' : '草稿' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="60" align="center">
            <template #default="{row}">
              <span style="color:#999">v{{ row.version }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="165" align="center"
                           :formatter="fmtDate" />
          <el-table-column label="操作" width="230" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click.stop="showDetail(row)">查看</el-button>
              <el-button size="small" text type="primary" @click.stop="showEdit(row)">编辑</el-button>
              <el-button size="small" text type="warning" @click.stop="showVersions(row)">版本</el-button>
              <el-button size="small" text type="danger" @click.stop="deleteDoc(row.id)">删除</el-button>
              <el-button size="small" text @click.stop="showAcl(row)">🔒 权限</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div style="margin-top:16px;display:flex;justify-content:space-between;align-items:center">
          <span style="font-size:13px;color:#999">共 {{ total }} 条记录</span>
          <el-pagination background layout="prev,next,sizes,total" :total="total"
                         v-model:page-size="pageSize" v-model:current-page="pageNum"
                         :page-sizes="[10,20,50]" @change="loadDocs" />
        </div>
      </el-card>
    </div>
  </div>

  <!-- 新建/编辑弹窗 -->
  <el-dialog :title="editing ? '编辑文档' : '新建文档'" v-model="dialogVisible" width="620px"
             :close-on-click-modal="false">
    <el-form :model="form" label-width="70px" label-position="left">
      <el-form-item label="标题">
        <el-input v-model="form.title" placeholder="请输入文档标题" />
      </el-form-item>
      <el-form-item label="内容">
        <el-input v-model="form.content" type="textarea" :rows="5" placeholder="请输入文档正文" />
      </el-form-item>
      <el-form-item label="分类">
        <el-tree-select v-model="form.categoryId" :data="categories"
          :props="{ label:'name', value:'id', children:'children' }"
          placeholder="请选择分类（可选）" clearable check-strictly style="width:100%" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status" style="width:140px">
          <el-option label="✅ 已发布" value="PUBLISHED" />
          <el-option label="📝 草稿" value="DRAFT" />
        </el-select>
      </el-form-item>
      <el-form-item label="附件">
        <el-upload :auto-upload="false" :limit="1" :on-change="onFileChange"
                   :file-list="fileList" accept=".pdf,.doc,.docx,.txt,.jpg,.png,.xls,.xlsx,.ppt,.pptx">
          <el-button size="small" plain>选择文件</el-button>
          <template #tip>
            <span style="font-size:12px;color:#999;margin-left:8px">
              {{ editing ? '留空则不更新原文件' : '可选' }}
            </span>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveDoc" :disabled="!form.title">保存</el-button>
    </template>
  </el-dialog>

  <!-- 版本历史 -->
  <el-drawer v-model="versionVisible" title="版本历史" size="420px">
    <el-timeline v-if="versions.length">
      <el-timeline-item v-for="v in versions" :key="v.version"
        :timestamp="(v.createTime||'').replace('T',' ').substring(0,19)" placement="top"
        :color="v.version===versions.length ? '#409EFF' : '#c0c4cc'">
        <el-card shadow="hover" style="margin-bottom:8px">
          <div style="font-weight:600">版本 {{ v.version }}</div>
          <div style="font-size:13px;color:#666;margin-top:4px">{{ v.changeDesc || '无描述' }}</div>
          <div v-if="v.fileUrl" style="font-size:12px;color:#999;margin-top:2px;word-break:break-all">
            📎 {{ v.fileUrl }}
          </div>
          <el-button size="small" text type="primary" @click="revertVersion(v.version)" style="margin-top:6px">
            回滚到此版本
          </el-button>
        </el-card>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="暂无版本记录" />
  </el-drawer>

  <!-- 文档详情（正文 + 附件） -->
  <el-dialog v-model="detailVisible" :title="detailDoc.title" width="720px" top="5vh">
    <div v-loading="detailLoading">
      <!-- 元信息 -->
      <div style="display:flex;gap:20px;margin-bottom:16px;font-size:13px;color:#999;flex-wrap:wrap">
        <span>👤 {{ detailDoc.creatorName }}</span>
        <span>🕒 {{ (detailDoc.updateTime||'').replace('T',' ').substring(0,19) }}</span>
        <span>📌 v{{ detailDoc.version }}</span>
        <el-tag :type="detailDoc.status==='PUBLISHED'?'success':'info'" size="small" effect="plain">
          {{ detailDoc.status==='PUBLISHED' ? '已发布' : '草稿' }}
        </el-tag>
      </div>
      <!-- 正文 -->
      <div style="padding:16px;background:#fafafa;border-radius:8px;white-space:pre-wrap;
                  word-break:break-word;line-height:1.8;min-height:80px;max-height:360px;
                  overflow-y:auto;font-size:14px;color:#333">
        {{ detailDoc.content || '暂无正文内容' }}
      </div>
      <!-- 附件区 -->
      <div v-if="detailFiles.length" style="margin-top:20px">
        <div style="font-weight:600;font-size:14px;margin-bottom:10px;color:#333">
          📎 附件 ({{ detailFiles.length }})
        </div>
        <el-table :data="detailFiles" border size="small">
          <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
          <el-table-column label="大小" width="100" align="center">
            <template #default="{row}">{{ fmtFileSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="上传时间" width="165" align="center">
            <template #default="{row}">{{ fmtDate(null, null, row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center">
            <template #default="{row}">
              <el-button size="small" text type="primary" @click="previewFile(row.id)">预览</el-button>
              <el-button size="small" text type="success" @click="downloadFile(row.id)">下载</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div v-else style="margin-top:20px;padding:12px;background:#fafafa;border-radius:6px;
                          text-align:center;font-size:13px;color:#bbb">
        暂无附件
      </div>
    </div>
  </el-dialog>

  <!-- ACL 权限管理 -->
  <el-dialog v-model="aclVisible" width="620px" :close-on-click-modal="false">
    <template #header>
      <span style="font-weight:600">文档权限管理 -《{{ aclDocTitle }}》</span>
    </template>

    <el-table :data="aclRows" border size="small" v-loading="aclLoading">
      <el-table-column label="用户" min-width="160">
        <template #default="{row}">
          <template v-if="row.isNew">
            <el-select v-model="row.userId" placeholder="搜索选择用户" size="small" style="width:100%"
                       filterable @change="onAclUserChange(row)">
              <el-option v-for="u in userList" :key="u.id"
                         :label="u.realName + ' (' + u.username + ')'" :value="u.id"
                         :disabled="aclRows.some(r => !r.isNew && r.userId === u.id)" />
            </el-select>
          </template>
          <template v-else>
            {{ resolveUserName(row.userId) }}
          </template>
        </template>
      </el-table-column>
      <el-table-column label="读" width="80" align="center">
        <template #default="{row}">
          <el-switch v-model="row.permRead" size="small"
                     @change="row.isNew ? null : saveAclRow(row)" />
        </template>
      </el-table-column>
      <el-table-column label="写" width="80" align="center">
        <template #default="{row}">
          <el-switch v-model="row.permWrite" size="small"
                     @change="row.isNew ? null : saveAclRow(row)" />
        </template>
      </el-table-column>
      <el-table-column label="下载" width="90" align="center">
        <template #default="{row}">
          <el-switch v-model="row.permDownload" size="small"
                     @change="row.isNew ? null : saveAclRow(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template #default="{row}">
          <el-button v-if="row.isNew" size="small" type="primary"
                     @click="saveAclRow(row)" :disabled="!row.userId">
            保存
          </el-button>
          <el-button v-else size="small" text type="danger"
                     @click="revokeAcl(row)">
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top:12px">
      <el-button size="small" text type="primary" @click="addAclRow"
                 :disabled="aclRows.some(r => r.isNew)">
        + 添加用户权限
      </el-button>
      <span style="font-size:12px;color:#bbb;margin-left:8px">
        创建者默认拥有全部权限，无需额外设置
      </span>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import http from '../api/request.js'

const keyword = ref('')
const statusFilter = ref('')
const categoryIds = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const docs = ref([])
const loading = ref(false)

const categories = ref([])
const treeRef = ref(null)
const newCatName = ref('')
const selectedCatId = ref(null)

const dialogVisible = ref(false)
const editing = ref(false)
const currentId = ref(null)
const form = reactive({ title: '', content: '', categoryId: null, status: 'PUBLISHED' })

const versionVisible = ref(false)
const versions = ref([])
const currentDocId = ref(null)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailDoc = reactive({})
const detailFiles = ref([])

const aclVisible = ref(false)
const aclDocTitle = ref('')
const aclLoading = ref(false)
const aclRows = ref([])
const userList = ref([])

const fileList = ref([])
const uploadedFile = ref(null)

const onFileChange = (file) => {
  fileList.value = [file]
  uploadedFile.value = file.raw
}

onMounted(() => { loadDocs(); loadCategories() })

const loadDocs = async () => {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: pageSize.value }
  if (keyword.value) params.keyword = keyword.value
  if (statusFilter.value) params.status = statusFilter.value
  if (categoryIds.value.length > 0) params.categoryIds = categoryIds.value
  const { data } = await http.get('/doc/page', { params })
  if (data.data) {
    docs.value = data.data.records || []
    total.value = data.data.total || 0
  }
  loading.value = false
}

const loadCategories = async () => {
  const { data } = await http.get('/doc/category/tree')
  if (data.data) categories.value = data.data
}

const addCategory = async () => {
  if (!newCatName.value) return
  const params = { name: newCatName.value }
  if (selectedCatId.value) params.parentId = selectedCatId.value
  await http.post('/doc/category', null, { params })
  newCatName.value = ''
  selectedCatId.value = null
  loadCategories()
}

const onTreeCheck = () => {
  // 收集所有勾选的节点 ID（含半选父节点）
  const nodes = treeRef.value.getCheckedNodes(false, true)
  categoryIds.value = nodes.map(n => n.id)
  pageNum.value = 1
  loadDocs()
}

const clearCategories = () => {
  treeRef.value.setCheckedKeys([])
  categoryIds.value = []
  pageNum.value = 1
  loadDocs()
}

const onNodeClick = (node) => {
  selectedCatId.value = node.id
}

const showCreate = () => {
  editing.value = false; currentId.value = null
  const defaultCat = categoryIds.value.length === 1 ? categoryIds.value[0] : null
  Object.assign(form, { title: '', content: '', categoryId: defaultCat, status: 'PUBLISHED' })
  fileList.value = []
  uploadedFile.value = null
  dialogVisible.value = true
}

const showEdit = (row) => {
  editing.value = true; currentId.value = row.id
  Object.assign(form, { title: row.title, content: row.content || '',
    categoryId: row.categoryId ?? null, status: row.status })
  fileList.value = []
  uploadedFile.value = null
  dialogVisible.value = true
}

const saveDoc = async () => {
  // 1. 保存文档，获取 docId
  let docId = currentId.value
  if (editing.value) {
    await http.put('/doc/' + docId, form)
  } else {
    const res = await http.post('/doc', form)
    if (res.data.data && res.data.data.id) docId = res.data.data.id
  }
  // 2. 上传文件（关联文档）
  if (uploadedFile.value && docId) {
    const fd = new FormData()
    fd.append('file', uploadedFile.value)
    fd.append('docId', docId)
    await http.post('/file/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
  dialogVisible.value = false
  loadDocs()
}

const deleteDoc = async (id) => {
  if (!confirm('确认删除该文档？')) return
  await http.delete('/doc/' + id)
  loadDocs()
}

const showDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  // 加载文档详情
  try {
    const { data: d } = await http.get(`/doc/${row.id}`)
    if (d.data) Object.assign(detailDoc, d.data)
  } catch (e) { /* ignore */ }
  // 加载附件列表
  try {
    const { data: f } = await http.get(`/file/by-doc/${row.id}`)
    detailFiles.value = f.data || []
  } catch (e) { detailFiles.value = [] }
  detailLoading.value = false
  currentDocId.value = row.id
}

const showAcl = async (row) => {
  currentDocId.value = row.id
  aclDocTitle.value = row.title
  aclVisible.value = true
  aclLoading.value = true
  try {
    const [_users, _acl] = await Promise.allSettled([
      http.get('/auth/users'),
      http.get('/doc/' + row.id + '/acl')
    ])
    userList.value = _users.status === 'fulfilled' ? (_users.value.data?.data || []) : []
    const aclList = _acl.status === 'fulfilled' ? (_acl.value.data?.data || []) : []
    aclRows.value = aclList.map(a => ({
      id: a.id,
      userId: a.userId,
      permRead: !!a.permRead,
      permWrite: !!a.permWrite,
      permDownload: !!a.permDownload,
      isNew: false
    }))
  } finally {
    aclLoading.value = false
  }
}

const showVersions = async (row) => {
  currentDocId.value = row.id
  const { data } = await http.get('/doc/' + row.id + '/versions')
  versions.value = data.data || []
  versionVisible.value = true
}

const revertVersion = async (ver) => {
  if (!confirm('确认回滚到版本 ' + ver + ' ？')) return
  await http.post('/doc/' + currentDocId.value + '/revert/' + ver)
  versionVisible.value = false
  loadDocs()
}

const loadAcl = async () => {
  try {
    const { data } = await http.get('/doc/' + currentDocId.value + '/acl')
    const aclList = data.data || []
    aclRows.value = aclList.map(a => ({
      id: a.id,
      userId: a.userId,
      permRead: !!a.permRead,
      permWrite: !!a.permWrite,
      permDownload: !!a.permDownload,
      isNew: false
    }))
  } catch(e) {
    aclRows.value = aclRows.value.filter(r => r.isNew)
  }
}

const addAclRow = () => {
  aclRows.value.push({
    userId: null,
    permRead: true,
    permWrite: false,
    permDownload: false,
    isNew: true
  })
}

const onAclUserChange = (row) => {
  // 用户选中后，默认给读权限
  row.permRead = true
}

const saveAclRow = async (row) => {
  if (!row.userId) return
  try {
    await http.post('/doc/' + currentDocId.value + '/acl', {
      userId: row.userId,
      permRead: row.permRead ? 1 : 0,
      permWrite: row.permWrite ? 1 : 0,
      permDownload: row.permDownload ? 1 : 0
    })
    await loadAcl()
  } catch (e) { /* error handled by interceptor */ }
}

const resolveUserName = (userId) => {
  const u = userList.value.find(u => u.id === userId)
  return u ? u.realName + ' (' + u.username + ')' : '用户#' + userId
}

const revokeAcl = async (row) => {
  if (!confirm('确认移除该用户的权限？')) return
  try {
    await http.delete('/doc/' + currentDocId.value + '/acl/' + row.userId)
    await loadAcl()
  } catch (e) { /* error handled by interceptor */ }
}

const previewFile = (fileId) => {
  window.open(`/file/preview/${fileId}`)
}

const downloadFile = (fileId) => {
  window.open(`/file/download/${fileId}`)
}

const fmtFileSize = (bytes) => {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

const fmtDate = (_row, _col, val) => {
  if (!val) return ''
  return val.replace('T', ' ').substring(0, 19)
}
</script>
