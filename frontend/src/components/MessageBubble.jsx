import ReactMarkdown from 'react-markdown'
import './MessageBubble.css'

function MessageBubble({ message }) {
  const isUser = message.role === 'user'
  const isLoading = message.loading
  const isError = message.error

  return (
    <div className={`bubble-row ${isUser ? 'user-row' : 'assistant-row'}`}>
      <div className="avatar">
        {isUser ? '👤' : '🤖'}
      </div>
      <div className={`bubble ${isUser ? 'user-bubble' : 'assistant-bubble'} ${isError ? 'error-bubble' : ''}`}>
        {isLoading ? (
          <div className="loading-dots">
            <span /><span /><span />
          </div>
        ) : isUser ? (
          <p className="user-text">{message.content}</p>
        ) : (
          <div className="markdown-body">
            <ReactMarkdown>{message.content}</ReactMarkdown>
          </div>
        )}
      </div>
    </div>
  )
}

export default MessageBubble
