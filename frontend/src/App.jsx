import { useState, useCallback, useEffect, useRef } from 'react'
import { v4 as uuidv4 } from 'uuid'
import Sidebar from './components/Sidebar'
import ChatWindow from './components/ChatWindow'
import AuthPage from './components/AuthPage'
import { authFetch } from './api'
import './App.css'

const DEFAULT_SYSTEM_PROMPT = 'You are a helpful assistant. Be concise and clear.'

const newBlankSession = () => ({
  id: uuidv4(),
  name: 'New Chat',
  messages: [],
  systemPrompt: DEFAULT_SYSTEM_PROMPT,
})

function App() {
  const [token, setToken] = useState(() => localStorage.getItem('token'))
  const [username, setUsername] = useState(() => localStorage.getItem('username') ?? '')
  const [sessions, setSessions] = useState([])
  const [activeSessionId, setActiveSessionId] = useState(null)
  // ready=false means we are initializing (show spinner), ready=true means show UI
  const [ready, setReady] = useState(false)
  const loadingRef = useRef(false)

  const activeSession = sessions.find(s => s.id === activeSessionId)

  useEffect(() => {
    // No token — nothing to load, just show auth page
    if (!token) {
      setSessions([])
      setActiveSessionId(null)
      setReady(true)
      return
    }

    // Prevent concurrent loads (e.g. from fast token changes)
    if (loadingRef.current) return
    loadingRef.current = true
    setReady(false)

    let cancelled = false

    const load = async () => {
      try {
        const res = await authFetch('/api/chat/sessions')
        if (cancelled) return

        // Token expired or invalid — log out
        if (res.status === 401) {
          localStorage.removeItem('token')
          localStorage.removeItem('username')
          if (!cancelled) {
            setToken(null)
            setUsername('')
            setSessions([])
            setActiveSessionId(null)
          }
          return
        }

        let newSessions = []

        if (res.ok) {
          const data = await res.json()
          if (cancelled) return

          const sessionList = data.sessions ?? []

          if (sessionList.length > 0) {
            const withMessages = await Promise.all(
              sessionList.map(async (s) => {
                try {
                  const msgRes = await authFetch(`/api/chat/session/${s.id}/messages`)
                  const msgData = await msgRes.json()
                  return {
                    id: s.id,
                    name: s.name,
                    messages: (msgData.messages ?? []).map(m => ({
                      role: m.role,
                      content: m.content,
                      timestamp: Date.now(),
                    })),
                    systemPrompt: DEFAULT_SYSTEM_PROMPT,
                  }
                } catch {
                  return { id: s.id, name: s.name, messages: [], systemPrompt: DEFAULT_SYSTEM_PROMPT }
                }
              })
            )
            if (cancelled) return
            newSessions = withMessages
          }
        }

        // Always start with a fresh new chat on login,
        // but keep previous sessions visible in the sidebar
        const fresh = newBlankSession()
        newSessions = [fresh, ...newSessions]

        if (!cancelled) {
          setSessions(newSessions)
          setActiveSessionId(fresh.id)
        }
      } catch {
        if (cancelled) return
        const s = newBlankSession()
        setSessions([s])
        setActiveSessionId(s.id)
      } finally {
        loadingRef.current = false
        if (!cancelled) setReady(true)
      }
    }

    load()

    return () => {
      cancelled = true
      loadingRef.current = false
    }
  }, [token])

  const handleAuth = useCallback((newToken, newUsername) => {
    setReady(false) // show spinner immediately on login
    setToken(newToken)
    setUsername(newUsername)
  }, [])

  const handleLogout = useCallback(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    setToken(null)
    setUsername('')
    setSessions([])
    setActiveSessionId(null)
  }, [])

  const createSession = useCallback(() => {
    const s = newBlankSession()
    setSessions(prev => [s, ...prev])
    setActiveSessionId(s.id)
  }, [])

  const deleteSession = useCallback(async (sessionId) => {
    try {
      await authFetch(`/api/chat/session/${sessionId}`, { method: 'DELETE' })
    } catch { /* ignore */ }

    setSessions(prev => {
      const remaining = prev.filter(s => s.id !== sessionId)
      if (activeSessionId === sessionId) {
        setActiveSessionId(remaining[0]?.id ?? null)
      }
      return remaining
    })
  }, [activeSessionId])

  const autoRenameSession = useCallback((sessionId, firstMessage) => {
    const name = firstMessage.length > 30
      ? firstMessage.substring(0, 30) + '…'
      : firstMessage
    setSessions(prev =>
      prev.map(s => s.id === sessionId ? { ...s, name } : s)
    )
  }, [])

  const sendMessage = useCallback(async (content) => {
    if (!activeSession) return

    const userMsg = { role: 'user', content, timestamp: Date.now() }
    setSessions(prev => prev.map(s =>
      s.id === activeSessionId ? { ...s, messages: [...s.messages, userMsg] } : s
    ))

    if (activeSession.messages.length === 0) {
      autoRenameSession(activeSessionId, content)
    }

    const loadingMsg = { role: 'assistant', content: '...', loading: true, timestamp: Date.now() }
    setSessions(prev => prev.map(s =>
      s.id === activeSessionId ? { ...s, messages: [...s.messages, loadingMsg] } : s
    ))

    try {
      const res = await authFetch('/api/chat/memory', {
        method: 'POST',
        body: JSON.stringify({
          sessionId: activeSessionId,
          message: content,
          systemPrompt: activeSession.systemPrompt,
        }),
      })

      if (res.status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('username')
        setToken(null)
        setUsername('')
        setSessions([])
        setActiveSessionId(null)
        return
      }

      const data = await res.json()
      const assistantMsg = {
        role: 'assistant',
        content: data.response ?? data.error ?? 'No response.',
        timestamp: Date.now(),
      }

      setSessions(prev => prev.map(s => {
        if (s.id !== activeSessionId) return s
        return { ...s, messages: [...s.messages.filter(m => !m.loading), assistantMsg] }
      }))

    } catch {
      setSessions(prev => prev.map(s => {
        if (s.id !== activeSessionId) return s
        return {
          ...s,
          messages: [...s.messages.filter(m => !m.loading), {
            role: 'assistant',
            content: 'Network error. Is the backend running on port 8080?',
            error: true,
            timestamp: Date.now(),
          }]
        }
      }))
    }
  }, [activeSession, activeSessionId, autoRenameSession])

  const updateSystemPrompt = useCallback((prompt) => {
    setSessions(prev => prev.map(s =>
      s.id === activeSessionId ? { ...s, systemPrompt: prompt } : s
    ))
  }, [activeSessionId])

  // Show spinner until initialization is complete
  if (!ready) {
    return (
      <div className="loading-screen">
        <div className="loading-spinner" />
        <p>Loading…</p>
      </div>
    )
  }

  // Not logged in
  if (!token) {
    return <AuthPage onAuth={handleAuth} />
  }

  // Logged in but no session selected (e.g. all sessions deleted)
  if (!activeSession) {
    return (
      <div className="app">
        <Sidebar
          sessions={sessions}
          activeSessionId={activeSessionId}
          onSelect={setActiveSessionId}
          onCreate={createSession}
          onDelete={deleteSession}
          username={username}
          onLogout={handleLogout}
        />
        <div className="empty-state">
          <button className="new-chat-btn" onClick={createSession}>+ New Chat</button>
        </div>
      </div>
    )
  }

  return (
    <div className="app">
      <Sidebar
        sessions={sessions}
        activeSessionId={activeSessionId}
        onSelect={setActiveSessionId}
        onCreate={createSession}
        onDelete={deleteSession}
        username={username}
        onLogout={handleLogout}
      />
      <ChatWindow
        session={activeSession}
        onSend={sendMessage}
        onUpdateSystemPrompt={updateSystemPrompt}
      />
    </div>
  )
}

export default App
