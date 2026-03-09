import './SystemPromptBar.css'

function SystemPromptBar({ value, onChange }) {
  return (
    <div className="system-prompt-bar">
      <label className="sp-label">System Prompt</label>
      <textarea
        className="sp-textarea"
        value={value}
        onChange={e => onChange(e.target.value)}
        rows={3}
        placeholder="You are a helpful assistant..."
        spellCheck={false}
      />
      <p className="sp-hint">
        Defines how the model behaves. Only applied at the start of a new session.
      </p>
    </div>
  )
}

export default SystemPromptBar
