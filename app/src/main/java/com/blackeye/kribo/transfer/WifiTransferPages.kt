package com.blackeye.kribo.transfer

/**
 * Offline HTML for the local Wi-Fi transfer page.
 *
 * Everything is inline: no CDNs, no external fonts and no network calls outside the
 * Kribo server itself. The session cookie is HttpOnly, so the scripts never read it.
 */
internal object WifiTransferPages {

    val LOGIN = """
<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="dark light">
<title>Kribo · PIN</title>
<style>
:root{color-scheme:dark;--bg:#0D1117;--panel:#161B22;--line:#30363D;--text:#FFFFFF;--muted:#9BA7B4;--accent:#00D8FF;--accent-ink:#00232B}
@media (prefers-color-scheme:light){:root{color-scheme:light;--bg:#F6F8FA;--panel:#FFFFFF;--line:#D0D7DE;--text:#0D1117;--muted:#5C6670;--accent:#007A99;--accent-ink:#FFFFFF}}
*{box-sizing:border-box}
[hidden]{display:none!important}
body{margin:0;min-height:100vh;display:grid;place-items:center;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;padding:20px}
.card{background:var(--panel);border:1px solid var(--line);border-radius:24px;padding:28px;max-width:380px;width:100%;box-shadow:0 24px 60px -30px rgba(0,0,0,.55)}
.brand{display:flex;align-items:center;gap:12px;margin-bottom:16px}
.brand svg{width:46px;height:46px;flex-shrink:0;border-radius:14px}
h1{margin:0;font-size:24px}
p{color:var(--muted);font-size:14px;line-height:1.5}
input{width:100%;margin:16px 0;padding:14px;border-radius:14px;border:1px solid var(--line);background:var(--bg);color:var(--text);font-size:24px;letter-spacing:8px;text-align:center;outline:none}
input:focus{border-color:var(--accent)}
button{width:100%;min-height:48px;padding:14px;border-radius:14px;border:0;background:var(--accent);color:var(--accent-ink);font-weight:700;font-size:16px;cursor:pointer}
.err{color:#FF6B6B;font-size:13px;margin:0 0 8px}
</style>
</head>
<body>
<main class="card">
<div class="brand">
<svg viewBox="0 0 108 108" aria-hidden="true">
<rect width="108" height="108" rx="24" fill="#0D1117"/>
<path d="M32 27 L32 81" stroke="#FFFFFF" stroke-width="9" stroke-linecap="round"/>
<path d="M66 27 L40 53" stroke="#FFFFFF" stroke-width="9" stroke-linecap="round"/>
<path d="M40 55 L62 78" stroke="#FFFFFF" stroke-width="9" stroke-linecap="round"/>
<path d="M58 72 L80 94 L60 88 Z" fill="#00D8FF"/>
</svg>
<h1>Kribo</h1>
</div>
<p>Introduce el PIN de 6 dígitos que aparece en tu dispositivo.</p>
<p id="err" class="err" hidden>PIN incorrecto. Inténtalo de nuevo.</p>
<input id="pin" inputmode="numeric" maxlength="6" autocomplete="one-time-code" autofocus aria-label="PIN de 6 dígitos">
<button id="go" type="button">Entrar</button>
</main>
<script>
(function () {
  var input = document.getElementById('pin');
  var button = document.getElementById('go');
  var error = document.getElementById('err');
  if (new URLSearchParams(location.search).get('error') === '1') {
    error.hidden = false;
  }
  function clean() {
    input.value = input.value.replace(/[^0-9]/g, '').slice(0, 6);
  }
  async function submit() {
    clean();
    if (input.value.length !== 6) {
      error.hidden = false;
      error.textContent = 'Escribe los 6 dígitos del PIN.';
      return;
    }
    var response = await fetch('/api/login?pin=' + encodeURIComponent(input.value), { credentials: 'same-origin' });
    if (response.ok) {
      location.replace('/');
    } else {
      error.hidden = false;
      error.textContent = 'PIN incorrecto. Inténtalo de nuevo.';
      input.select();
    }
  }
  button.addEventListener('click', submit);
  input.addEventListener('input', clean);
  input.addEventListener('keydown', function (event) {
    if (event.key === 'Enter') {
      event.preventDefault();
      submit();
    }
  });
})();
</script>
</body>
</html>
""".trimIndent()

    val APP = """
<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="dark light">
<title>Kribo · Transferencia local</title>
<style>
:root{color-scheme:dark;--bg:#0D1117;--panel:#161B22;--panel-2:#1C232C;--line:#30363D;--text:#FFFFFF;--muted:#9BA7B4;--accent:#00D8FF;--accent-ink:#00232B;--ok:#34D399;--ok-soft:rgba(52,211,153,.14);--danger:#FF6B6B;--shadow:0 24px 60px -30px rgba(0,0,0,.6);--img-bg:#000000}
@media (prefers-color-scheme:light){:root{color-scheme:light;--bg:#F6F8FA;--panel:#FFFFFF;--panel-2:#EEF2F6;--line:#D0D7DE;--text:#0D1117;--muted:#5C6670;--accent:#007A99;--accent-ink:#FFFFFF;--ok:#047857;--ok-soft:rgba(4,120,87,.12);--danger:#B3261E;--shadow:0 24px 60px -30px rgba(13,17,23,.28);--img-bg:#EEF2F6}}
*{box-sizing:border-box}
[hidden]{display:none!important}
html,body{height:100%}
body{margin:0;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;display:flex;flex-direction:column;overflow:hidden}
button,input,textarea{font:inherit}
button{cursor:pointer}
.top{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap;padding:14px 20px;border-bottom:1px solid var(--line);background:var(--panel);flex-shrink:0}
.brand{display:flex;align-items:center;gap:12px;min-width:0}
.brand svg{width:44px;height:44px;border-radius:14px;flex-shrink:0;box-shadow:var(--shadow)}
.brand h1{margin:0;font-size:22px;line-height:1.1}
.badge{display:inline-flex;align-items:center;gap:6px;margin-top:6px;padding:4px 9px;border-radius:999px;background:var(--ok-soft);color:var(--ok);border:1px solid var(--ok-soft);font-size:12px;font-weight:600;max-width:100%}
.badge i{width:6px;height:6px;border-radius:50%;background:var(--ok);flex-shrink:0}
.top-actions{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
.primary,.ghost{display:inline-flex;align-items:center;justify-content:center;gap:7px;min-height:44px;padding:0 15px;border-radius:12px;font-weight:650;font-size:14px;white-space:nowrap;text-decoration:none;transition:border-color .15s,background .15s,color .15s}
.primary{background:var(--accent);border:1px solid var(--accent);color:var(--accent-ink)}
.ghost{background:var(--panel-2);border:1px solid var(--line);color:var(--text)}
.ghost:hover,.primary:hover{filter:brightness(1.05)}
.ghost:focus-visible,.primary:focus-visible,input:focus-visible,textarea:focus-visible,.note-card:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
.layout{flex:1;min-height:0;display:grid;grid-template-columns:360px minmax(0,1fr);gap:14px;padding:14px 20px}
.panel{min-height:0;display:flex;flex-direction:column;background:var(--panel);border:1px solid var(--line);border-radius:18px;overflow:hidden;box-shadow:var(--shadow)}
.panel-head{padding:12px;border-bottom:1px solid var(--line);flex-shrink:0}
.search{display:flex;align-items:center;gap:9px;background:var(--panel-2);border:1px solid var(--line);border-radius:12px;padding:0 11px}
.search svg{width:17px;height:17px;fill:none;stroke:var(--muted);stroke-width:1.8;stroke-linecap:round;flex-shrink:0}
.search input{min-width:0;flex:1;border:0;background:transparent;color:var(--text);padding:11px 0;outline:none;font-size:14px}
.list-meta{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:9px}
.muted{color:var(--muted);font-size:12.5px;line-height:1.45}
.back{display:none;min-height:36px;padding:0 11px;border-radius:10px;font-size:13px}
.list-scroll{flex:1;min-height:0;overflow-y:auto;padding:8px;display:flex;flex-direction:column;gap:7px}
.empty{padding:26px 14px;color:var(--muted);font-size:14px;text-align:center;line-height:1.55}
.note-card{width:100%;display:flex;flex-direction:column;gap:5px;text-align:left;background:transparent;border:1px solid transparent;border-radius:12px;padding:11px 12px;color:var(--text)}
.note-card:hover{background:var(--panel-2)}
.note-card.active{background:var(--panel-2);border-color:var(--accent)}
.note-card .title-row{display:flex;align-items:center;gap:7px;min-width:0}
.note-card .note-title{min-width:0;flex:1;font-size:14px;font-weight:650;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.pill{flex-shrink:0;padding:2px 6px;border-radius:999px;background:var(--ok-soft);color:var(--ok);font-size:10px;font-weight:700;letter-spacing:.04em}
.note-card .note-preview{color:var(--muted);font-size:12.5px;line-height:1.4;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;overflow-wrap:anywhere}
.note-card .note-date{color:var(--muted);font-size:11px}
.panel-view{min-height:0;position:relative}
.mobile-bar{display:none;padding:10px 12px 0;flex-shrink:0}
.view-scroll{flex:1;min-height:0;overflow-y:auto;padding:18px 20px}
.view-head{display:flex;align-items:flex-start;justify-content:space-between;gap:14px;margin-bottom:10px}
.view-title{min-width:0;flex:1;margin:0;font-size:22px;line-height:1.25;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.view-actions{display:flex;align-items:center;gap:7px;flex-shrink:0;flex-wrap:wrap;justify-content:flex-end}
.view-actions .ghost,.view-actions .primary{min-height:38px;padding:0 11px;font-size:13px}
.view-actions a{flex-shrink:0;white-space:nowrap}
.note-body{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.62;font-size:14.5px;margin:14px 0}
h3{margin:20px 0 8px;font-size:14px;color:var(--muted);font-weight:650;letter-spacing:.02em;text-transform:uppercase}
pre.note-body{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:12.5px;background:var(--panel-2);border:1px solid var(--line);border-radius:12px;padding:12px;white-space:pre-wrap}
.attachments{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:10px;margin-top:14px}
.attachments img{width:100%;height:220px;object-fit:contain;background:var(--img-bg);border:1px solid var(--line);border-radius:12px}
.attachments a.file{display:flex;align-items:center;min-height:44px;padding:10px 12px;border-radius:12px;border:1px solid var(--line);background:var(--panel-2);color:var(--accent);text-decoration:none;font-size:13px;overflow-wrap:anywhere}
.read-only{margin-top:14px;padding:10px 12px;border-radius:12px;background:var(--panel-2);border:1px solid var(--line);color:var(--muted);font-size:13px;line-height:1.45}
.empty-state{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;padding:30px;text-align:center}
.empty-state svg{width:64px;height:64px;opacity:.55}
.editor{display:flex;flex-direction:column}
.field{display:flex;flex-direction:column;gap:6px;margin-bottom:14px}
.field span{font-size:12px;font-weight:650;color:var(--muted)}
.field input,.field textarea{width:100%;border:1px solid var(--line);border-radius:12px;background:var(--panel-2);color:var(--text);padding:11px 12px;outline:none}
.field textarea{min-height:220px;resize:vertical;line-height:1.55}
.form-actions{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-top:4px}
.foot{flex-shrink:0;padding:9px 20px 12px;color:var(--muted);font-size:11.5px;line-height:1.45;text-align:center;border-top:1px solid var(--line)}
@media (max-width:899px){
  body{overflow:hidden}
  .layout{grid-template-columns:1fr;padding:10px;gap:0}
  .panel-list{display:flex}
  .panel-view{display:none}
  body.show-detail .panel-list{display:none}
  body.show-detail .panel-view{display:flex}
  .mobile-bar{display:flex}
  .back{display:inline-flex}
  .top{padding:11px 14px}
  .top-actions{width:100%;flex-direction:column;align-items:stretch}
  .top-actions .primary,.top-actions .ghost{width:100%}
  .view-scroll{padding:14px}
  .view-head{flex-wrap:wrap}
  .view-title{white-space:normal;overflow:visible;text-overflow:clip}
  .view-actions{width:100%;justify-content:flex-start}
}
</style>
</head>
<body>
<header class="top">
  <div class="brand">
    <svg viewBox="0 0 108 108" aria-hidden="true">
      <rect width="108" height="108" rx="24" fill="#0D1117"/>
      <path d="M32 27 L32 81" stroke="#FFFFFF" stroke-width="9" stroke-linecap="round"/>
      <path d="M66 27 L40 53" stroke="#FFFFFF" stroke-width="9" stroke-linecap="round"/>
      <path d="M40 55 L62 78" stroke="#FFFFFF" stroke-width="9" stroke-linecap="round"/>
      <path d="M58 72 L80 94 L60 88 Z" fill="#00D8FF"/>
    </svg>
    <div>
      <h1>Kribo</h1>
      <span class="badge"><i></i>Conectado en red local</span>
    </div>
  </div>
  <div class="top-actions">
    <button id="newBtn" class="primary" type="button">+ Nueva nota</button>
    <button id="backupBtn" class="ghost" type="button">Descargar backup .zip</button>
  </div>
</header>

<main class="layout">
  <section class="panel panel-list" aria-label="Lista de notas">
    <div class="panel-head">
      <label class="search">
        <svg viewBox="0 0 20 20" aria-hidden="true"><circle cx="9" cy="9" r="5.5"/><path d="m13.2 13.2 3.8 3.8"/></svg>
        <input id="q" type="search" placeholder="Buscar notas y OCR…" autocomplete="off" aria-label="Buscar notas y OCR">
      </label>
      <div class="list-meta">
        <span id="count" class="muted">0 notas</span>
      </div>
    </div>
    <div id="list" class="list-scroll" role="list"></div>
  </section>

  <section class="panel panel-view" aria-label="Visor de nota">
    <div class="mobile-bar">
      <button id="backBtn" class="ghost back" type="button">← Notas</button>
    </div>

    <div id="emptyView" class="empty-state">
      <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M6 3.5h8.5L19 8v12.5H6z"/><path d="M14.5 3.5V8H19"/><path d="M9 12h6M9 15.5h6M9 8.5h2"/>
      </svg>
      <p class="muted">Selecciona una nota de la lista o crea una nueva.</p>
    </div>

    <div id="noteView" class="view-scroll" hidden>
      <div class="view-head">
        <h2 id="vTitle" class="view-title"></h2>
        <div class="view-actions">
          <button id="copyBtn" class="ghost" type="button">Copiar texto</button>
          <a id="mdLink" class="ghost" href="#" download>Descargar .md</a>
          <button id="editBtn" class="ghost" type="button">Editar</button>
        </div>
      </div>
      <div id="vMeta" class="muted"></div>
      <div id="vBody" class="note-body" hidden></div>
      <div id="vOcrWrap" hidden>
        <h3>Texto OCR</h3>
        <pre id="vOcr" class="note-body"></pre>
      </div>
      <div id="vAttach" class="attachments" hidden></div>
      <p id="vReadOnly" class="read-only" hidden>Esta nota contiene bloques ricos o imágenes. Edítala en la app de Android.</p>
    </div>

    <div id="editorView" class="view-scroll editor" hidden>
      <form id="noteForm">
        <div class="view-head">
          <h2 id="editorTitle" class="view-title">Nueva nota</h2>
          <div class="view-actions">
            <button id="cancelBtn" class="ghost" type="button">Cancelar</button>
          </div>
        </div>
        <label class="field"><span>Título</span><input id="fTitle" type="text" maxlength="120" placeholder="Título de la nota"></label>
        <label class="field"><span>Texto</span><textarea id="fText" placeholder="Escribe el contenido…"></textarea></label>
        <div class="form-actions">
          <button type="submit" class="primary" id="saveBtn">Guardar en Kribo</button>
          <span id="formMsg" class="muted" role="status"></span>
        </div>
      </form>
    </div>
  </section>
</main>

<footer class="foot">El aviso «No seguro» del navegador es normal: la conexión es directa en tu Wi-Fi local, sin servidores externos. Usa esta página solo en una red de confianza.</footer>

<script>
(function () {
  var notes = [];
  var selectedId = null;
  var current = null;
  var requestId = 0;
  var searchTimer = null;

  function byId(id) { return document.getElementById(id); }
  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  // LinkBlock URLs surface as "label https://…" text here. Only http(s) becomes a
  // clickable anchor in a new tab; javascript:/file:/content:/intent: stay inert text.
  function linkify(rawText) {
    var plain = esc(rawText);
    return plain.replace(/https?:\/\/[^\s<>"']+/g, function (url) {
      var clean = url.replace(/[.,;:!?)\]]+$/, '');
      if (!/^https?:\/\//i.test(clean)) return url;
      var trail = url.substring(clean.length);
      return '<a href="' + clean + '" target="_blank" rel="noopener noreferrer">' + clean + '</a>' + esc(trail);
    });
  }
  function formatDate(value) {
    if (!value) return '';
    try {
      return new Date(value).toLocaleString('es', { dateStyle: 'medium', timeStyle: 'short' });
    } catch (error) {
      return '';
    }
  }
  function showEmpty() {
    byId('emptyView').hidden = false;
    byId('noteView').hidden = true;
    byId('editorView').hidden = true;
  }
  function setDetailVisible(visible) {
    if (visible) document.body.classList.add('show-detail');
    else document.body.classList.remove('show-detail');
  }

  async function loadNotes() {
    var q = byId('q').value.trim();
    var token = ++requestId;
    try {
      var response = await fetch('/api/notes?q=' + encodeURIComponent(q), { credentials: 'same-origin' });
      if (!response.ok) throw new Error('session');
      var data = await response.json();
      if (token !== requestId) return;
      notes = data;
      renderList();
    } catch (error) {
      notes = [];
      byId('count').textContent = '';
      byId('list').innerHTML = '<p class="empty">Sesión cerrada. Vuelve a entrar con el PIN desde la app.</p>';
    }
  }

  function renderList() {
    var list = byId('list');
    if (!notes.length) {
      list.innerHTML = '<p class="empty">Sin notas. Crea la primera con «+ Nueva nota».</p>';
    } else {
      var html = '';
      notes.forEach(function (note) {
        var active = note.id === selectedId ? ' active' : '';
        html += '<button type="button" role="listitem" data-id="' + note.id + '" class="note-card' + active + '">';
        html += '<span class="title-row"><span class="note-title">' + esc(note.title) + '</span>';
        if (note.hasOcr) html += '<span class="pill">OCR</span>';
        html += '</span>';
        if (note.preview) html += '<span class="note-preview">' + esc(note.preview) + '</span>';
        html += '<span class="note-date">' + esc(formatDate(note.updatedAt)) + '</span>';
        html += '</button>';
      });
      list.innerHTML = html;
      var cards = list.querySelectorAll('.note-card');
      Array.prototype.forEach.call(cards, function (card) {
        card.addEventListener('click', function () {
          selectNote(Number(card.getAttribute('data-id')));
        });
      });
    }
    byId('count').textContent = notes.length + (notes.length === 1 ? ' nota' : ' notas');
  }

  async function selectNote(id) {
    try {
      var response = await fetch('/api/notes/' + id, { credentials: 'same-origin' });
      if (!response.ok) throw new Error('note');
      current = await response.json();
      selectedId = id;
      setDetailVisible(true);
      renderDetail();
      renderList();
    } catch (error) {
      byId('list').innerHTML = '<p class="empty">No se pudo abrir la nota. La sesión puede haber terminado.</p>';
    }
  }

  function renderDetail() {
    if (!current) return;
    byId('emptyView').hidden = true;
    byId('editorView').hidden = true;
    byId('noteView').hidden = false;
    byId('vTitle').textContent = current.title || 'Sin título';
    byId('mdLink').href = '/api/notes/' + current.id + '/export';
    byId('vMeta').textContent = formatDate(current.updatedAt) + (current.editable ? '' : ' · Solo lectura');
    // bodyHtml comes from the server fully escaped (only whitelisted span tags remain).
    byId('vBody').innerHTML = current.bodyHtml || linkify(current.body || '');
    byId('vBody').hidden = !current.body;
    byId('vOcr').textContent = current.ocrText || '';
    byId('vOcrWrap').hidden = !current.ocrText;
    byId('vReadOnly').hidden = current.editable;
    byId('editBtn').hidden = !current.editable;
    byId('copyBtn').textContent = 'Copiar texto';
    renderAttachments(current.attachments || []);
  }

  function renderAttachments(items) {
    var host = byId('vAttach');
    host.innerHTML = '';
    if (!items.length) {
      host.hidden = true;
      return;
    }
    host.hidden = false;
    items.forEach(function (item) {
      if ((item.mimeType || '').indexOf('image/') === 0) {
        var image = document.createElement('img');
        image.src = item.url;
        image.alt = item.fileName || 'Imagen adjunta';
        image.loading = 'lazy';
        host.appendChild(image);
      } else {
        var link = document.createElement('a');
        link.className = 'file';
        link.href = item.url;
        link.target = '_blank';
        link.rel = 'noreferrer';
        link.textContent = item.fileName || 'Adjunto';
        host.appendChild(link);
      }
    });
  }

  async function copyText(text) {
    try {
      await navigator.clipboard.writeText(text);
    } catch (error) {
      var area = document.createElement('textarea');
      area.value = text;
      area.setAttribute('readonly', 'readonly');
      area.style.position = 'fixed';
      area.style.opacity = '0';
      document.body.appendChild(area);
      area.select();
      document.execCommand('copy');
      area.remove();
    }
  }

  async function copyCurrent() {
    if (!current) return;
    var parts = [current.title, current.body, current.ocrText].filter(function (part) {
      return part && part.trim();
    });
    await copyText(parts.join('\n\n'));
    var button = byId('copyBtn');
    button.textContent = 'Copiado';
    setTimeout(function () { button.textContent = 'Copiar texto'; }, 1600);
  }

  function showNew() {
    current = null;
    selectedId = null;
    byId('emptyView').hidden = true;
    byId('noteView').hidden = true;
    byId('editorView').hidden = false;
    byId('editorTitle').textContent = 'Nueva nota';
    byId('fTitle').value = '';
    byId('fText').value = '';
    byId('formMsg').textContent = '';
    setDetailVisible(true);
    setTimeout(function () { byId('fTitle').focus(); }, 30);
  }

  function editCurrent() {
    if (!current || !current.editable) return;
    byId('emptyView').hidden = true;
    byId('noteView').hidden = true;
    byId('editorView').hidden = false;
    byId('editorTitle').textContent = 'Editar nota';
    byId('fTitle').value = current.title || '';
    byId('fText').value = current.body || '';
    byId('formMsg').textContent = '';
    setDetailVisible(true);
    setTimeout(function () { byId('fTitle').focus(); }, 30);
  }

  function cancelEdit() {
    if (current) {
      renderDetail();
    } else {
      showEmpty();
      setDetailVisible(false);
    }
  }

  async function saveNote(event) {
    event.preventDefault();
    var title = byId('fTitle').value.trim();
    var text = byId('fText').value.trim();
    if (!title && !text) {
      byId('formMsg').textContent = 'Escribe un título o un texto.';
      return;
    }
    var editingId = current && current.editable ? current.id : null;
    var url = editingId ? '/api/notes/' + editingId : '/api/notes';
    var method = editingId ? 'PUT' : 'POST';
    try {
      var response = await fetch(url, {
        method: method,
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: title, text: text })
      });
      if (!response.ok) {
        byId('formMsg').textContent = response.status === 409
          ? 'Esta nota se edita en la app de Android.'
          : 'No se pudo guardar.';
        return;
      }
      var data = await response.json();
      byId('formMsg').textContent = 'Guardada';
      if (!editingId) byId('q').value = '';
      await loadNotes();
      await selectNote(data.id);
    } catch (error) {
      byId('formMsg').textContent = 'No se pudo guardar.';
    }
  }

  function backToList() {
    setDetailVisible(false);
  }

  byId('q').addEventListener('input', function () {
    if (searchTimer) clearTimeout(searchTimer);
    searchTimer = setTimeout(loadNotes, 120);
  });
  byId('q').addEventListener('keydown', function (event) {
    if (event.key === 'Enter') {
      event.preventDefault();
      if (searchTimer) clearTimeout(searchTimer);
      loadNotes();
      byId('q').blur();
    }
  });
  byId('newBtn').addEventListener('click', showNew);
  byId('backupBtn').addEventListener('click', function () { location.href = '/api/backup.zip'; });
  byId('backBtn').addEventListener('click', backToList);
  byId('copyBtn').addEventListener('click', copyCurrent);
  byId('editBtn').addEventListener('click', editCurrent);
  byId('cancelBtn').addEventListener('click', cancelEdit);
  byId('noteForm').addEventListener('submit', saveNote);

  loadNotes();
})();
</script>
</body>
</html>
""".trimIndent()
}
