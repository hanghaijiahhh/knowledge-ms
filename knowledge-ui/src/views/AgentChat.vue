<template>
  <div style="height:calc(100vh - 120px);display:flex;flex-direction:column">
    <el-card shadow="never" style="flex:1;display:flex;flex-direction:column;overflow:hidden">
      <template #header>
        <div style="display:flex;align-items:center;gap:8px">
          <span style="font-size:18px">🤖</span>
          <span style="font-weight:600">AI 智能问答</span>
          <el-tag size="small" type="info" effect="plain">DeepSeek</el-tag>
        </div>
      </template>

      <!-- 对话区 -->
      <div ref="chatBox" style="flex:1;overflow-y:auto;padding:4px 0;min-height:0">
        <div v-if="!messages.length" style="text-align:center;color:#999;padding:80px 0">
          <div style="font-size:56px;margin-bottom:20px">🤖</div>
          <div style="font-size:16px;font-weight:500;color:#666;margin-bottom:8px">
            知识库 AI 助手
          </div>
          <div style="font-size:13px;color:#999;line-height:1.8">
            <div>我可以帮你检索企业内部文档并回答</div>
            <div style="margin-top:12px">
              <el-tag style="margin:2px 4px;cursor:pointer" size="small" effect="plain"
                      @click="question='请假流程是什么？'; send()">请假流程是什么？</el-tag>
              <el-tag style="margin:2px 4px;cursor:pointer" size="small" effect="plain"
                      @click="question='有哪些分类？'; send()">有哪些分类？</el-tag>
              <el-tag style="margin:2px 4px;cursor:pointer" size="small" effect="plain"
                      @click="question='介绍一下Java体系'; send()">介绍一下Java体系</el-tag>
            </div>
          </div>
        </div>

        <div v-for="(msg, i) in messages" :key="i" style="margin-bottom:20px">
          <!-- 用户消息 -->
          <div v-if="msg.role==='user'" style="display:flex;justify-content:flex-end;padding:0 8px">
            <div style="background:#409EFF;color:#fff;padding:10px 16px;border-radius:16px 16px 4px 16px;
                        max-width:75%;font-size:14px;line-height:1.6;word-break:break-word;box-shadow:0 2px 6px rgba(64,158,255,0.2)">
              {{ msg.content }}
            </div>
          </div>
          <!-- AI 消息 -->
          <div v-else style="display:flex;gap:10px;padding:0 8px">
            <div style="width:36px;height:36px;background:linear-gradient(135deg,#409EFF,#337ecc);color:#fff;
                        border-radius:50%;display:flex;align-items:center;justify-content:center;
                        font-size:13px;font-weight:600;flex-shrink:0;box-shadow:0 2px 6px rgba(64,158,255,0.15)">
              AI
            </div>
            <div style="flex:1;max-width:80%">
              <div style="background:#f7f8fa;padding:10px 16px;border-radius:4px 16px 16px 16px;
                          white-space:pre-wrap;word-break:break-word;font-size:14px;line-height:1.7;color:#333">
                {{ msg.content }}
              </div>
              <!-- 思考链 -->
              <div v-if="msg.thoughtTrace && msg.thoughtTrace.length" style="margin-top:8px">
                <el-collapse style="background:transparent;border:none">
                  <el-collapse-item>
                    <template #title>
                      <span style="font-size:12px;color:#909399">
                        🔍 思考过程（{{ msg.thoughtTrace.length }} 步）
                      </span>
                    </template>
                    <div v-for="(s, si) in msg.thoughtTrace" :key="si"
                         style="font-size:12px;color:#666;padding:8px 12px;background:#fafafa;
                                border-radius:6px;margin-bottom:6px;border-left:2px solid #409EFF">
                      <div style="font-weight:600;margin-bottom:2px">
                        第{{ s.turn + 1 }}轮 · 调用 {{ s.action }}
                      </div>
                      <div style="color:#999;word-break:break-all">{{ truncate(s.observation, 300) }}</div>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </div>
          </div>
        </div>

        <div v-if="thinking" style="padding:8px 16px;color:#909399;font-size:13px;display:flex;align-items:center;gap:8px">
          <span>AI 正在思考</span>
          <span class="dot-pulse"></span>
        </div>
      </div>

      <!-- 输入区 -->
      <div style="padding:12px 0 0;border-top:1px solid #f0f0f0;display:flex;gap:10px;align-items:flex-end">
        <el-input v-model="question" placeholder="输入问题，如：请假流程是什么？"
                  @keyup.enter="send" :disabled="thinking" clearable
                  :autosize="{ minRows: 1, maxRows: 4 }" type="textarea"
                  resize="none" style="flex:1" />
        <el-button type="primary" @click="send" :loading="thinking" :disabled="!question.trim()"
                   style="height:40px;padding:0 24px;font-weight:500">
          发送
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import http from '../api/request.js'

const question = ref('')
const messages = ref([])
const thinking = ref(false)
const sessionId = ref('')
const chatBox = ref(null)

const send = async () => {
  const q = question.value.trim()
  if (!q || thinking.value) return
  question.value = ''

  messages.value.push({ role: 'user', content: q })
  thinking.value = true
  await scrollBottom()

  try {
    const { data } = await http.post('/agent/chat', {
      question: q,
      sessionId: sessionId.value || null
    })
    const resp = data.data
    if (resp) {
      sessionId.value = resp.sessionId
      messages.value.push({
        role: 'ai',
        content: resp.answer || '抱歉，未能获取回答。',
        thoughtTrace: resp.thoughtTrace || []
      })
    }
  } catch (e) {
    messages.value.push({ role: 'ai', content: '请求失败，请稍后重试。' })
  } finally {
    thinking.value = false
    await scrollBottom()
  }
}

const scrollBottom = async () => {
  await nextTick()
  if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight
}

const truncate = (text, max) => {
  if (!text) return ''
  return text.length > max ? text.substring(0, max) + '...' : text
}
</script>

<style scoped>
.dot-pulse::after {
  content: '';
  animation: dots 1.5s steps(4, end) infinite;
}
@keyframes dots {
  0% { content: ''; }
  25% { content: '.'; }
  50% { content: '..'; }
  75% { content: '...'; }
}
</style>
