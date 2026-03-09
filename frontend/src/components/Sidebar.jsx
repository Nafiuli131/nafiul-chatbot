import './Sidebar.css'

function Sidebar({ sessions, activeSessionId, onSelect, onCreate, onDelete, username, onLogout }) {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <span className="sidebar-title">Nafiul Chatbot</span>
        <button className="new-btn" onClick={onCreate} title="New Chat">+</button>
      </div>

      <div className="session-list">
        {sessions.map(session => (
          <div
            key={session.id}
            className={`session-item ${session.id === activeSessionId ? 'active' : ''}`}
            onClick={() => onSelect(session.id)}
          >
            <span className="session-icon">💬</span>
            <span className="session-name">{session.name}</span>
            <button
              className="delete-btn"
              onClick={(e) => {
                e.stopPropagation()
                onDelete(session.id)
              }}
              title="Delete session"
            >
              ✕
            </button>
          </div>
        ))}
      </div>

      <div className="sidebar-footer">
        <span className="powered-by">Nafiul Chatbot</span>
        <div className="user-row">
          <span className="user-name">👤 {username}</span>
          <button className="logout-btn" onClick={onLogout} title="Logout">Logout</button>
        </div>
      </div>
    </aside>
  )
}

export default Sidebar
