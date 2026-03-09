import { useState, useRef, useEffect } from 'react'
import MessageBubble from './MessageBubble'
import SystemPromptBar from './SystemPromptBar'
import './ChatWindow.css'

function ChatWindow({ session, onSend, onUpdateSystemPrompt }) {
  const [input, setInput] = useState('')
  const [showSystemPrompt, setShowSystemPrompt] = useState(false)
  const bottomRef = useRef(null)
  const textareaRef = useRef(null)

  const isLoading = session.messages.some(m => m.loading)

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [session.messages])

  const handleSend = () => {
    const text = input.trim()
    if (!text || isLoading) return
    setInput('')
    onSend(text)
    textareaRef.current?.focus()
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="chat-window">
      {/* Header */}
      <div className="chat-header">
        <div className="chat-header-left">
          <span className="chat-title">{session.name}</span>
          <span className="message-count">{session.messages.length} messages</span>
        </div>
        <button
          className={`system-prompt-toggle ${showSystemPrompt ? 'active' : ''}`}
          onClick={() => setShowSystemPrompt(p => !p)}
          title="System prompt"
        >
          ⚙ System Prompt
        </button>
      </div>

      {/* System Prompt Editor */}
      {showSystemPrompt && (
        <SystemPromptBar
          value={session.systemPrompt}
          onChange={onUpdateSystemPrompt}
        />
      )}

      {/* Messages */}
      <div className="messages-container">
        {session.messages.length === 0 ? (
          <div className="empty-chat">
            <div className="empty-chat-icon">🧠</div>
            <h2>Nafiul Chatbot</h2>
            <p>Your personal AI assistant</p>
            <p className="hint">Type a message below to start chatting</p>
          </div>
        ) : (
          session.messages.map((msg, idx) => (
            <MessageBubble key={idx} message={msg} />
          ))
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="input-area">
        <div className="input-box">
          <textarea
            ref={textareaRef}
            className="input-textarea"
            placeholder="Ask Nafiul... (Enter to send, Shift+Enter for new line)"
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={1}
            disabled={isLoading}
          />
          <button
            className="send-btn"
            onClick={handleSend}
            disabled={!input.trim() || isLoading}
          >
            {isLoading ? (
              <span className="spinner" />
            ) : (
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
              </svg>
            )}
          </button>
        </div>
        <p className="input-hint">Session: {session.id.substring(0, 8)}…</p>
      </div>
    </div>
  )
}

export default ChatWindow
