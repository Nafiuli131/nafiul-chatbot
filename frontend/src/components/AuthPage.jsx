import { useState } from 'react'
import './AuthPage.css'

function AuthPage({ onAuth }) {
  const [mode, setMode] = useState('login') // 'login' | 'register'
  const [form, setForm] = useState({ username: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleChange = (e) => {
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }))
    setError('')
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')

    const url = mode === 'login' ? '/api/auth/login' : '/api/auth/register'
    const body = mode === 'login'
      ? { username: form.username, password: form.password }
      : { username: form.username, email: form.email, password: form.password }

    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })

      const data = await res.json()

      if (!res.ok) {
        setError(data.error || 'Something went wrong')
        return
      }

      localStorage.setItem('token', data.token)
      localStorage.setItem('username', data.username)
      onAuth(data.token, data.username)

    } catch {
      setError('Cannot connect to server. Is the backend running?')
    } finally {
      setLoading(false)
    }
  }

  const switchMode = () => {
    setMode(m => m === 'login' ? 'register' : 'login')
    setError('')
    setForm({ username: '', email: '', password: '' })
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-logo">🧠</div>
        <h1 className="auth-title">Nafiul Chatbot</h1>
        <p className="auth-subtitle">Your personal AI assistant</p>

        <div className="auth-tabs">
          <button
            className={`auth-tab ${mode === 'login' ? 'active' : ''}`}
            onClick={() => { setMode('login'); setError('') }}
          >
            Login
          </button>
          <button
            className={`auth-tab ${mode === 'register' ? 'active' : ''}`}
            onClick={() => { setMode('register'); setError('') }}
          >
            Register
          </button>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="auth-field">
            <label>Username</label>
            <input
              name="username"
              type="text"
              value={form.username}
              onChange={handleChange}
              placeholder="Enter username"
              required
              autoFocus
            />
          </div>

          {mode === 'register' && (
            <div className="auth-field">
              <label>Email</label>
              <input
                name="email"
                type="email"
                value={form.email}
                onChange={handleChange}
                placeholder="Enter email"
                required
              />
            </div>
          )}

          <div className="auth-field">
            <label>Password</label>
            <input
              name="password"
              type="password"
              value={form.password}
              onChange={handleChange}
              placeholder="Enter password"
              required
            />
          </div>

          {error && <p className="auth-error">{error}</p>}

          <button className="auth-submit" type="submit" disabled={loading}>
            {loading ? 'Please wait…' : mode === 'login' ? 'Login' : 'Create Account'}
          </button>
        </form>

        <p className="auth-switch">
          {mode === 'login' ? "Don't have an account?" : 'Already have an account?'}
          {' '}
          <button className="auth-switch-btn" onClick={switchMode}>
            {mode === 'login' ? 'Register' : 'Login'}
          </button>
        </p>
      </div>
    </div>
  )
}

export default AuthPage
