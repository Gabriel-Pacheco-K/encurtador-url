const $ = (id) => document.getElementById(id);
let chart = null;
let currentCode = null;

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    const fieldErrors = body?.errors ? Object.values(body.errors).join(' · ') : null;
    throw new Error(fieldErrors || body?.detail || 'Algo deu errado. Tente novamente.');
  }
  return body;
}

// ─── Encurtar ────────────────────────────────────────────────
$('shorten-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const error = $('form-error');
  error.hidden = true;

  const payload = { url: $('url').value.trim() };
  const alias = $('alias').value.trim();
  const expires = $('expires').value;
  if (alias) payload.alias = alias;
  if (expires) payload.expiresAt = new Date(expires).toISOString();

  const button = $('submit-btn');
  button.disabled = true;
  button.textContent = 'Encurtando…';

  try {
    const result = await api('/api/urls', { method: 'POST', body: JSON.stringify(payload) });
    $('result-url').textContent = result.shortUrl.replace(/^https?:\/\//, '');
    $('result-url').href = result.shortUrl;
    $('result-token').textContent = result.manageToken;
    $('result').hidden = false;
    $('shorten-form').reset();
    $('stats-code').value = result.code;
    loadStats(result.code);
  } catch (err) {
    error.textContent = err.message;
    error.hidden = false;
  } finally {
    button.disabled = false;
    button.textContent = 'Encurtar';
  }
});

$('copy-btn').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText($('result-url').href);
    $('copy-btn').textContent = 'Copiado!';
  } catch {
    $('copy-btn').textContent = 'Selecione e copie';
  }
  setTimeout(() => ($('copy-btn').textContent = 'Copiar'), 1800);
});

// ─── Estatísticas ────────────────────────────────────────────
$('stats-form').addEventListener('submit', (event) => {
  event.preventDefault();
  const code = $('stats-code').value.trim().split('/').pop();
  if (code) loadStats(code);
});

$('refresh-btn').addEventListener('click', () => currentCode && loadStats(currentCode));

async function loadStats(code) {
  try {
    const stats = await api(`/api/urls/${encodeURIComponent(code)}/stats`);
    currentCode = code;
    renderStats(stats);
    history.replaceState(null, '', `#${code}`);
  } catch (err) {
    $('stats').hidden = true;
    alert(err.message);
  }
}

function renderStats(stats) {
  $('stats').hidden = false;
  $('stats-code-label').textContent = `/${stats.code}`;
  $('stats-original').textContent = stats.originalUrl;
  $('stats-original').href = stats.originalUrl;

  const today = stats.clicksByDay.at(-1)?.clicks ?? 0;
  $('kpi-total').textContent = stats.totalClicks.toLocaleString('pt-BR');
  $('kpi-today').textContent = today.toLocaleString('pt-BR');
  $('kpi-top').textContent = stats.topReferrers[0]?.key ?? '—';

  const status = $('kpi-status');
  status.textContent = stats.expired ? 'Expirado' : 'Ativo';
  status.classList.toggle('expired', stats.expired);
  $('kpi-status-label').textContent = stats.expiresAt
    ? `expira em ${new Date(stats.expiresAt).toLocaleString('pt-BR')}`
    : 'sem data de expiração';

  renderChart(stats.clicksByDay);
  renderBars('list-referrers', stats.topReferrers);
  renderBars('list-browsers', stats.browsers);
  renderBars('list-devices', stats.devices);
}

function renderChart(days) {
  const labels = days.map((d) => new Date(d.date + 'T00:00:00').toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }));
  const data = days.map((d) => d.clicks);

  if (chart) chart.destroy();
  chart = new Chart($('chart-days'), {
    type: 'bar',
    data: {
      labels,
      datasets: [{ data, backgroundColor: '#4ade80', hoverBackgroundColor: '#86efac', borderRadius: 4, maxBarThickness: 18 }],
    },
    options: {
      maintainAspectRatio: false,
      animation: matchMedia('(prefers-reduced-motion: reduce)').matches ? false : undefined,
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: (ctx) => `${ctx.parsed.y} clique(s)` } },
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: '#6b7a99', maxRotation: 0, autoSkipPadding: 12 } },
        y: { beginAtZero: true, grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#6b7a99', precision: 0 } },
      },
    },
  });
}

function renderBars(listId, items) {
  const list = $(listId);
  list.replaceChildren();

  if (!items.length) {
    const empty = document.createElement('li');
    empty.className = 'empty';
    empty.textContent = 'Sem cliques ainda';
    list.append(empty);
    return;
  }

  const max = Math.max(...items.map((item) => item.count));
  for (const item of items) {
    const li = document.createElement('li');
    li.innerHTML = '<div class="bar-label"><span></span><span></span></div><div class="bar-track"><div class="bar-fill"></div></div>';
    const [name, count] = li.querySelectorAll('.bar-label span');
    name.textContent = item.key;
    count.textContent = item.count;
    li.querySelector('.bar-fill').style.width = `${(item.count / max) * 100}%`;
    list.append(li);
  }
}

// Abre direto nas estatísticas quando a URL tem #codigo
if (location.hash.length > 1) {
  const code = location.hash.slice(1);
  $('stats-code').value = code;
  loadStats(code);
}
