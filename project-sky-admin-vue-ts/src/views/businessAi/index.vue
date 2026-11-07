<template>
  <div class="business-ai-container">
    <div class="chat-header">
      <div>
        <h2>智能分析</h2>
        <p class="subtitle">
          AI 商家助手 - 销售分析 · 库存预测 · 补货建议
        </p>
      </div>
      <div class="header-actions">
        <el-button size="mini" icon="el-icon-plus" circle title="新建对话" @click="newConversation" />
        <el-button size="mini" icon="el-icon-s-order" circle title="历史会话" @click="openHistory" />
      </div>
    </div>
    <div class="quick-questions">
      <el-button size="small" @click="sendQuickQuestion('分析近7天销售趋势')">
        分析近7天销售趋势
      </el-button>
      <el-button size="small" @click="sendQuickQuestion('哪些商品即将缺货？')">
        哪些商品即将缺货？
      </el-button>
      <el-button size="small" @click="sendQuickQuestion('预测未来一周需要补货的商品')">
        预测未来一周需要补货的商品
      </el-button>
      <el-button size="small" @click="sendQuickQuestion('生成上个月销售报告')">
        生成上个月销售报告
      </el-button>
    </div>
    <div ref="chatMessages" class="chat-messages">
      <div v-for="(msg, index) in messages" :key="index"
           :class="['message', msg.role === 'user' ? 'user-message' : 'ai-message']"
      >
        <div class="avatar">
          <img v-if="msg.role === 'user'" src="@/assets/login/logo.png" alt="user">
          <span v-else class="ai-icon">AI</span>
        </div>
        <div class="bubble">
          <div class="content">
            {{ msg.content }}
          </div>
        </div>
      </div>
      <div v-if="streaming" class="message ai-message">
        <div class="avatar">
          <span class="ai-icon">AI</span>
        </div>
        <div class="bubble">
          <div class="content">
            {{ streamingContent }}<span class="cursor">|</span>
          </div>
        </div>
      </div>
    </div>
    <div class="chat-input">
      <el-input
        v-model="inputMessage"
        type="textarea"
        :rows="2"
        placeholder="输入您的问题..."
        @keydown.enter.native.prevent="sendMessage"
      />
      <el-button type="primary" :loading="loading" class="send-btn" @click="sendMessage">
        {{ loading ? '思考中...' : '发送' }}
      </el-button>
    </div>

    <!-- 历史会话 Dialog -->
    <el-dialog title="历史会话" :visible.sync="historyVisible" width="520px" :close-on-click-modal="false">
      <div v-if="sessions.length === 0" style="text-align:center;padding:24px;color:#999;">
        暂无历史会话
      </div>
      <div v-else class="history-list">
        <div v-for="s in sessions" :key="s.session_id" class="history-item" @click="switchSession(s.session_id)">
          <div class="hi-time">
            {{ formatTime(s.created_at) }}
          </div>
          <div class="hi-preview">
            {{ s.preview || '(空)' }}
          </div>
          <div class="hi-meta">
            共 {{ s.message_count }} 条消息
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import { getSessionList, getSessionMessages } from '@/api/businessAi'

interface ChatMessage {
  role: 'user' | 'ai'
  content: string
}

@Component({
  name: 'BusinessAi'
})
export default class extends Vue {
  private inputMessage = ''
  private messages: ChatMessage[] = []
  private loading = false
  private streaming = false
  private streamingContent = ''
  private historyVisible = false
  private sessions: any[] = []

  private get sessionId(): string {
    let sid = localStorage.getItem('admin_session_id')
    if (!sid) {
      sid = 'admin_' + Date.now()
      localStorage.setItem('admin_session_id', sid)
    }
    return sid
  }

  private set sessionId(val: string) {
    localStorage.setItem('admin_session_id', val)
  }

  private created() {
    if (!this.messages.length) {
      this.messages.push({
        role: 'ai',
        content: '您好！我是商家智能助手。我可以帮您分析销售数据、预测库存缺货、生成销售报告。请问有什么可以帮您的？'
      })
    }
  }

  private mounted() {
    this.loadHistory()
  }

  private async loadHistory() {
    const sid = this.sessionId
    try {
      const token = document.cookie.replace(/(?:(?:^|.*;\s*)token\s*=\s*([^;]*).*$)|^.*$/, '$1')
      const res = await fetch(`/api/aigc/sessions/${sid}/messages`, {
        headers: { 'token': token }
      })
      if (!res.ok) {
        console.error('[BusinessAi] 历史消息加载失败:', res.status, res.statusText)
        return
      }
      const json = await res.json()
      if (json.code === 1 && json.data && json.data.length > 0) {
        const msgs: ChatMessage[] = []
        for (const m of json.data) {
          const content = m.data ? m.data.content || '' : ''
          if (m.type === 'human') {
            msgs.push({ role: 'user', content: content })
          } else if (m.type === 'ai') {
            msgs.push({ role: 'ai', content: content })
          }
        }
        if (msgs.length > 0) {
          this.messages = msgs
        }
      }
    } catch (e) {
      console.error('[BusinessAi] 加载历史消息异常:', e)
    }
  }

  private formatTime(ts: number): string {
    const d = new Date(ts * 1000)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }

  private newConversation() {
    this.$confirm('确定开始新对话？当前对话将被保存。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      const sid = 'admin_' + Date.now()
      this.sessionId = sid
      this.messages = [{
        role: 'ai',
        content: '您好！我是商家智能助手。我可以帮您分析销售数据、预测库存缺货、生成销售报告。请问有什么可以帮您的？'
      }]
    }).catch(() => {})
  }

  private async openHistory() {
    this.historyVisible = true
    try {
      const res = await getSessionList('admin')
      this.sessions = (res as any).data ? (res as any).data.data || [] : []
    } catch (e) {
      this.sessions = []
    }
  }

  private async switchSession(sessionId: string) {
    this.historyVisible = false
    this.loading = true
    try {
      const res = await getSessionMessages(sessionId)
      const data = (res as any).data ? (res as any).data.data || [] : []
      const msgs: ChatMessage[] = [{ role: 'ai', content: '您好！我是商家智能助手。我可以帮您分析销售数据、预测库存缺货、生成销售报告。请问有什么可以帮您的？' }]
      let hasContent = false
      for (const m of data) {
        const content = m.data ? m.data.content || '' : ''
        if (m.type === 'human' && content) {
          msgs.push({ role: 'user', content })
          hasContent = true
        } else if (m.type === 'ai' && content) {
          msgs.push({ role: 'ai', content })
          hasContent = true
        }
      }
      if (hasContent) {
        this.messages = msgs
      }
      this.sessionId = sessionId
    } catch (e) {
      console.error('[BusinessAi] 切换会话失败:', e)
    } finally {
      this.loading = false
      this.$nextTick(() => this.scrollToBottom())
    }
  }

  private sendQuickQuestion(question: string) {
    this.inputMessage = question
    this.sendMessage()
  }

  private async sendMessage() {
    const text = this.inputMessage.trim()
    if (!text || this.loading) return

    this.messages = [...this.messages, { role: 'user', content: text }]
    this.inputMessage = ''
    this.loading = true
    this.streaming = true
    this.streamingContent = ''

    try {
      const token = document.cookie.replace(/(?:(?:^|.*;\s*)token\s*=\s*([^;]*).*$)|^.*$/, '$1')
      const resp = await fetch('/api/aigc/chat/stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'token': token
        },
        body: JSON.stringify({
          message: text,
          session_id: this.sessionId,
          role: 'admin'
        })
      })

      if (!resp.ok) {
        this.messages = [...this.messages, { role: 'ai', content: '抱歉，请求失败，请稍后重试。' }]
        this.streaming = false
        this.loading = false
        return
      }

      const reader = resp.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let chunkCount = 0

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          break
        }

        chunkCount++
        const text = decoder.decode(value, { stream: true })
        buffer += text
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const rawData = line.slice(5).trim()
            if (!rawData || rawData === '[DONE]') continue
            try {
              const parsed = JSON.parse(rawData)
              if (parsed.token) {
                this.streamingContent += parsed.token
                this.scrollToBottom()
              }
            } catch (e) {
              console.warn('[SSE] JSON解析失败:', rawData, e)
            }
          }
        }
      }

      if (this.streamingContent) {
        this.messages = [...this.messages, { role: 'ai', content: this.streamingContent }]
      }
    } catch (err) {
      console.error('[SSE] 请求异常:', err)
      this.messages = [...this.messages, { role: 'ai', content: '抱歉，请求失败：' + ((err as any).message || '未知错误') }]
    } finally {
      this.streaming = false
      this.streamingContent = ''
      this.loading = false
      this.scrollToBottom()
    }
  }

  private scrollToBottom() {
    this.$nextTick(() => {
      const container = this.$refs.chatMessages as HTMLElement
      if (container) container.scrollTop = container.scrollHeight
    })
  }
}
</script>

<style lang="scss" scoped>
.business-ai-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  background: #f5f7fa;
  border-radius: 8px;
  overflow: hidden;
}
.chat-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  padding: 16px 24px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  h2 { margin: 0; font-size: 20px; }
  .subtitle { margin: 4px 0 0; font-size: 13px; opacity: 0.85; }
  .header-actions {
    display: flex; gap: 6px;
    .el-button {
      border: none; background: rgba(255,255,255,0.15); color: #fff;
      &:hover { background: rgba(255,255,255,0.3); }
    }
  }
}
.quick-questions {
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  .el-button {
    border-radius: 16px;
    font-size: 12px;
  }
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  .message {
    display: flex;
    margin-bottom: 16px;
    align-items: flex-start;
    .avatar {
      width: 36px; height: 36px; border-radius: 50%;
      flex-shrink: 0; display: flex; align-items: center; justify-content: center;
      overflow: hidden;
      img { width: 100%; height: 100%; object-fit: cover; }
      .ai-icon {
        background: #667eea; color: #fff; width: 100%; height: 100%;
        display: flex; align-items: center; justify-content: center;
        font-size: 12px; font-weight: bold; border-radius: 50%;
      }
    }
    .bubble {
      max-width: 70%; padding: 10px 16px; border-radius: 12px;
      font-size: 14px; line-height: 1.6;
      .content { word-break: break-word; white-space: pre-wrap; }
    }
    &.user-message {
      flex-direction: row-reverse;
      .bubble {
        background: #667eea; color: #fff; margin-right: 10px;
        border-bottom-right-radius: 4px;
      }
    }
    &.ai-message {
      .bubble {
        background: #fff; color: #333; margin-left: 10px;
        border-bottom-left-radius: 4px; box-shadow: 0 1px 4px rgba(0,0,0,0.08);
      }
    }
  }
}
.cursor {
  animation: blink 1s step-end infinite;
  color: #667eea;
}
@keyframes blink {
  50% { opacity: 0; }
}
.chat-input {
  padding: 16px 24px; background: #fff; border-top: 1px solid #e8e8e8;
  display: flex; gap: 12px; align-items: center;
  .el-textarea { flex: 1; }
  .send-btn { height: 60px; width: 100px; font-size: 14px; }
}

.history-list {
  max-height: 400px; overflow-y: auto;
}
.history-item {
  padding: 14px 16px; border: 1px solid #f0f0f0; border-radius: 8px;
  margin-bottom: 8px; cursor: pointer; transition: background 0.2s;
  &:hover { background: #fafafa; }
  .hi-time { font-size: 12px; color: #999; margin-bottom: 4px; }
  .hi-preview { font-size: 14px; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .hi-meta { font-size: 12px; color: #999; margin-top: 4px; }
}

</style>
