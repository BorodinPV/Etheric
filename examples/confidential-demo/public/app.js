const app = document.getElementById('app');

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...(options.headers || {}) },
    ...options,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error_description || data.error || `Request failed (${response.status})`);
  }
  return data;
}

function homeView(error) {
  const params = new URLSearchParams(window.location.search);
  const registered = params.get('registered') === '1';
  const oauthError = error || params.get('error');
  return `
    <div class="card">
      <h1>Etheric Confidential Demo</h1>
      <p>
        Server-side (confidential) client. The browser talks only to this Node BFF.
        <code>client_secret</code> is used on the server when calling Etheric
        <code>/token</code>, <code>/introspect</code> and <code>/revoke</code>.
      </p>
      <p class="note">Tokens live in an HttpOnly session cookie on the BFF — not in <code>sessionStorage</code>.</p>
      ${registered ? '<p class="success">Account created. Sign in to continue.</p>' : ''}
      ${oauthError ? `<p class="error">${escapeHtml(oauthError)}</p>` : ''}
      <div class="actions">
        <a href="/login"><button type="button">Login with Etheric</button></a>
        <a href="/register"><button type="button" class="secondary">Create account</button></a>
      </div>
    </div>
  `;
}

function dashboardView(session, extras = {}) {
  const id = session.idClaims || {};
  const access = session.accessClaims || {};
  const roles = Array.isArray(access.groups) ? access.groups.join(', ') : '—';
  const introspection = extras.introspection
    ? `<dl>
        <dt>active</dt><dd>${escapeHtml(String(extras.introspection.active))}</dd>
        <dt>sub</dt><dd>${escapeHtml(extras.introspection.sub || '—')}</dd>
        <dt>scope</dt><dd>${escapeHtml(extras.introspection.scope || '—')}</dd>
        <dt>client_id</dt><dd>${escapeHtml(extras.introspection.client_id || '—')}</dd>
      </dl>
      <pre>${escapeHtml(JSON.stringify(extras.introspection, null, 2))}</pre>`
    : '';
  return `
    <div class="card">
      <h1>Dashboard</h1>
      <p>Signed in via Authorization Code + <code>client_secret</code> on the BFF (no PKCE, no secret in the browser).</p>
      <dl>
        <dt>sub</dt><dd>${escapeHtml(id.sub)}</dd>
        <dt>preferred_username</dt><dd>${escapeHtml(id.preferred_username || '—')}</dd>
        <dt>email</dt><dd>${escapeHtml(id.email || '—')}</dd>
        <dt>roles</dt><dd>${escapeHtml(roles)}</dd>
        <dt>scope</dt><dd>${escapeHtml(session.scope || '—')}</dd>
        <dt>tokens in browser</dt><dd>${session.tokensInBrowser ? 'yes' : 'no'}</dd>
      </dl>
      <section class="introspection">
        <h2>Token introspection</h2>
        <p>RFC 7662 via BFF — Basic <code>${escapeHtml(session.clientId)}:****</code> is added on the server.</p>
        ${extras.introspecting && !extras.introspection ? '<p>Introspecting…</p>' : ''}
        ${introspection}
        <button type="button" data-action="introspect" ${extras.introspecting ? 'disabled' : ''}>
          ${extras.introspecting ? 'Introspecting…' : 'Introspect again'}
        </button>
      </section>
      ${extras.message ? `<p>${escapeHtml(extras.message)}</p>` : ''}
      ${extras.error ? `<p class="error">${escapeHtml(extras.error)}</p>` : ''}
      <div class="actions" style="margin-top:1.5rem">
        <button type="button" data-action="refresh">Refresh token</button>
        <button type="button" class="secondary" data-action="logout">Logout</button>
      </div>
    </div>
  `;
}

let currentSession = null;
let extras = {};

function render() {
  if (!currentSession?.authenticated) {
    app.innerHTML = homeView(extras.error);
    return;
  }
  app.innerHTML = dashboardView(currentSession, extras);
}

async function loadSession() {
  currentSession = await api('/api/session');
  if (currentSession.authenticated) {
    extras = { ...extras, introspecting: true };
    render();
    try {
      extras.introspection = await api('/api/introspect', { method: 'POST' });
      extras.error = null;
    } catch (err) {
      extras.error = err.message;
    } finally {
      extras.introspecting = false;
    }
  }
  render();
}

app.addEventListener('click', async (event) => {
  const button = event.target.closest('[data-action]');
  if (!button) {
    return;
  }
  const action = button.dataset.action;
  extras.error = null;
  extras.message = null;
  try {
    if (action === 'introspect') {
      extras.introspecting = true;
      render();
      extras.introspection = await api('/api/introspect', { method: 'POST' });
      extras.introspecting = false;
    } else if (action === 'refresh') {
      currentSession = await api('/api/refresh', { method: 'POST' });
      extras.message = 'Tokens refreshed on the BFF.';
      extras.introspection = await api('/api/introspect', { method: 'POST' });
    } else if (action === 'logout') {
      const result = await api('/api/logout', { method: 'POST' });
      window.location.href = result.redirect;
      return;
    }
  } catch (err) {
    extras.introspecting = false;
    extras.error = err.message;
  }
  render();
});

loadSession().catch((err) => {
  extras.error = err.message;
  render();
});
