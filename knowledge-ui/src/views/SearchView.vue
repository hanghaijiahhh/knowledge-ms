<template>
  <div>
    <el-card shadow="never">
      <!-- 搜索栏 -->
      <div style="max-width:640px;margin:0 auto 24px">
        <div style="display:flex;gap:10px">
          <el-input v-model="keyword" placeholder="输入关键词搜索知识库文档..." size="large"
                    @keyup.enter="search" clearable style="flex:1" />
          <el-button type="primary" size="large" @click="search" :loading="loading">搜索</el-button>
        </div>
        <!-- 热门搜索 -->
        <div v-if="hotList.length" style="margin-top:12px;display:flex;align-items:center;gap:6px;flex-wrap:wrap">
          <span style="font-size:12px;color:#bbb">热门：</span>
          <el-tag v-for="h in hotList" :key="h.keyword" size="small"
                  style="cursor:pointer" @click="keyword = h.keyword; search()"
                  effect="plain" type="info">
            {{ h.keyword }} <span style="color:#bbb">({{ h.count }})</span>
          </el-tag>
        </div>
      </div>

      <!-- 搜索结果 -->
      <div v-loading="loading">
        <div v-if="searched" style="margin-bottom:16px;font-size:13px;color:#999">
          搜索 "<b style="color:#333">{{ searchedKeyword }}</b>"，找到 <b style="color:#409EFF">{{ total }}</b> 条结果
        </div>

        <div v-for="item in results" :key="item.docId"
             style="padding:16px 0;border-bottom:1px solid #f0f0f0;cursor:pointer;
                    transition:all 0.2s"
             @click="viewDetail(item.docId)"
             @mouseenter="$event.currentTarget.style.background='#fafafa'"
             @mouseleave="$event.currentTarget.style.background='transparent'">
          <div style="font-size:16px;font-weight:500;color:#409EFF;margin-bottom:6px">
            <span v-if="item.titleHighlight" v-html="item.titleHighlight" />
            <span v-else>{{ item.title }}</span>
          </div>
          <div v-if="item.contentHighlight"
               style="color:#666;font-size:13px;line-height:1.6;display:-webkit-box;
                      -webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden"
               v-html="item.contentHighlight" />
          <div style="font-size:12px;color:#bbb;margin-top:6px;display:flex;align-items:center;gap:12px">
            <span>👤 {{ item.creatorName }}</span>
            <span>🕒 {{ (item.createTime || '').replace('T',' ').substring(0,19) }}</span>
          </div>
        </div>

        <el-empty v-if="searched && !results.length && !loading" description="未找到相关文档" />

        <div v-if="total > 10" style="margin-top:20px;display:flex;justify-content:center">
          <el-pagination background layout="prev,next" :total="total"
                         v-model:current-page="pageNum" :page-size="10" @change="search" />
        </div>
      </div>
    </el-card>

    <!-- 文档详情弹窗 -->
    <el-dialog v-model="dialogVisible" :title="docTitle" width="700px" top="5vh">
      <div v-loading="detailLoading"
           style="white-space:pre-wrap;word-break:break-word;min-height:120px;line-height:1.8;color:#333">
        {{ docContent }}
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import http from '../api/request.js'

const keyword = ref('')
const results = ref([])
const hotList = ref([])
const loading = ref(false)
const searched = ref(false)
const searchedKeyword = ref('')
const pageNum = ref(1)
const total = ref(0)

const search = async () => {
  if (!keyword.value) return
  loading.value = true; searched.value = true
  searchedKeyword.value = keyword.value
  try {
    const { data } = await http.get('/search', {
      params: { keyword: keyword.value, pageNum: pageNum.value, pageSize: 10 }
    })
    if (data.data) {
      results.value = data.data.items || []
      total.value = data.data.total || 0
    }
  } catch(e) {} finally { loading.value = false }
}

const loadHot = async () => {
  try {
    const { data } = await http.get('/search/hot')
    hotList.value = data.data || []
  } catch(e) {}
}

const dialogVisible = ref(false)
const docTitle = ref('')
const docContent = ref('')
const detailLoading = ref(false)

const viewDetail = async (docId) => {
  dialogVisible.value = true
  detailLoading.value = true
  docTitle.value = ''
  docContent.value = ''
  try {
    const { data } = await http.get(`/doc/${docId}`)
    if (data.data) {
      docTitle.value = data.data.title || ''
      docContent.value = data.data.content || '暂无内容'
    }
  } catch (e) {
    docContent.value = '获取文档内容失败'
  } finally {
    detailLoading.value = false
  }
}

onMounted(loadHot)
</script>
