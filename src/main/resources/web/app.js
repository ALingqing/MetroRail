/* MetroRail 调度台前端逻辑 —— 改完保存，刷新浏览器即可生效 */
(function () {
  'use strict';

  var $ = function (s) { return document.querySelector(s); };
  var token = new URLSearchParams(location.search).get('token');
  var q = token ? ('?token=' + encodeURIComponent(token)) : '';
  var REFRESH_MS = 2000;

  /* ================= 主题 ================= */
  var THEME_KEY = 'metrorail.theme';
  try { if (localStorage.getItem(THEME_KEY) === 'dark') { document.body.classList.add('dark'); } } catch (e) { /* 忽略 */ }
  syncThemeButton();
  $('#theme').onclick = function () {
    document.body.classList.toggle('dark');
    try {
      localStorage.setItem(THEME_KEY, document.body.classList.contains('dark') ? 'dark' : 'light');
    } catch (e) { /* 忽略 */ }
    syncThemeButton();
    invalidateAll();
  };
  function syncThemeButton() {
    $('#theme').textContent = document.body.classList.contains('dark') ? '日间' : '夜间';
  }
  function isDark() { return document.body.classList.contains('dark'); }
  function ink() { return isDark() ? '#f2f2f2' : '#000000'; }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  /* ================= Toast ================= */
  function toast(message, kind) {
    var stack = $('#toasts');
    if (!stack) return;
    var el = document.createElement('div');
    el.className = 'toast-item ' + (kind || 'info');
    el.textContent = message;
    stack.appendChild(el);
    setTimeout(function () {
      el.classList.add('out');
      setTimeout(function () { el.remove(); }, 300);
    }, 2600);
  }

  /* ================= 地图视图：拖拽平移 + 滚轮缩放 ================= */
  var view = { scale: 1, tx: 0, ty: 0 };
  var svgEl = null, dragging = false, lastX = 0, lastY = 0, viewInit = false;

  function applyView() {
    if (!svgEl) return;
    svgEl.style.transform = 'translate(' + view.tx + 'px,' + view.ty + 'px) scale(' + view.scale + ')';
    $('#mapinfo').textContent = '缩放 ' + Math.round(view.scale * 100) + '%';
  }
  function zoomAt(cx, cy, f) {
    var n = Math.min(6, Math.max(0.15, view.scale * f)), k = n / view.scale;
    view.tx = cx - (cx - view.tx) * k;
    view.ty = cy - (cy - view.ty) * k;
    view.scale = n;
    applyView();
  }
  function fit() {
    if (!svgEl) return;
    var b = $('#track').getBoundingClientRect();
    var w = parseFloat(svgEl.getAttribute('width')), h = parseFloat(svgEl.getAttribute('height'));
    view.scale = Math.max(0.15, Math.min(6, Math.min((b.width - 48) / w, (b.height - 48) / h)));
    view.tx = (b.width - w * view.scale) / 2;
    view.ty = (b.height - h * view.scale) / 2;
    applyView();
  }
  (function bindMap() {
    var el = $('#track');
    el.addEventListener('mousedown', function (e) {
      dragging = true; lastX = e.clientX; lastY = e.clientY;
      el.classList.add('dragging'); e.preventDefault();
    });
    window.addEventListener('mousemove', function (e) {
      if (!dragging) return;
      view.tx += e.clientX - lastX; view.ty += e.clientY - lastY;
      lastX = e.clientX; lastY = e.clientY; applyView();
    });
    window.addEventListener('mouseup', function () { dragging = false; el.classList.remove('dragging'); });
    el.addEventListener('wheel', function (e) {
      e.preventDefault();
      var b = el.getBoundingClientRect();
      zoomAt(e.clientX - b.left, e.clientY - b.top, e.deltaY < 0 ? 1.15 : 1 / 1.15);
    }, { passive: false });

    var ts = null, tp = null;
    var dist = function (t) { return Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY); };
    el.addEventListener('touchstart', function (e) {
      if (e.touches.length === 1) ts = { x: e.touches[0].clientX, y: e.touches[0].clientY };
      else if (e.touches.length === 2) tp = dist(e.touches);
    }, { passive: true });
    el.addEventListener('touchmove', function (e) {
      if (e.touches.length === 1 && ts) {
        view.tx += e.touches[0].clientX - ts.x; view.ty += e.touches[0].clientY - ts.y;
        ts = { x: e.touches[0].clientX, y: e.touches[0].clientY };
        applyView(); e.preventDefault();
      } else if (e.touches.length === 2 && tp) {
        var d = dist(e.touches), b = el.getBoundingClientRect();
        zoomAt(b.width / 2, b.height / 2, d / tp); tp = d; e.preventDefault();
      }
    }, { passive: false });
  })();

  /* ================= 轨道图绘制（服务器坐标 [x,y,z]） ================= */
  var FREE = '#3b82f6', BUSY = '#ef4444', STATION = '#f59e0b';

  function drawTrack(track) {
    if (!track || !track.length) {
      svgEl = null;
      $('#track').innerHTML = '<div class="legend"><div>暂无轨道图</div></div>'
        + '<div id="mapinfo">缩放 100%</div>';
      return;
    }
    var K = ink();
    var minX = 1e9, minZ = 1e9, maxX = -1e9, maxZ = -1e9, i, e, p, j;
    for (i = 0; i < track.length; i++) {
      e = track[i];
      for (j = 0; j < 2; j++) {
        p = j ? e.to : e.from;
        if (p[0] < minX) minX = p[0];
        if (p[0] > maxX) maxX = p[0];
        if (p[2] < minZ) minZ = p[2];
        if (p[2] > maxZ) maxZ = p[2];
      }
    }
    var pad = 48, scale = 42;
    var tx = function (v) { return pad + (v[0] - minX) * scale; };
    var ty = function (v) { return pad + (v[2] - minZ) * scale; };

    var s = '<svg width="' + ((maxX - minX) * scale + 2 * pad) + '" height="'
      + ((maxZ - minZ) * scale + 2 * pad) + '">';

    // 1) 黑色描边层
    for (i = 0; i < track.length; i++) {
      e = track[i];
      s += '<line x1="' + tx(e.from) + '" y1="' + ty(e.from) + '" x2="' + tx(e.to) + '" y2="' + ty(e.to)
        + '" stroke="' + K + '" stroke-width="13"/>';
    }
    // 2) 彩芯层：蓝=空闲，红=占用
    for (i = 0; i < track.length; i++) {
      e = track[i];
      s += '<line x1="' + tx(e.from) + '" y1="' + ty(e.from) + '" x2="' + tx(e.to) + '" y2="' + ty(e.to)
        + '" stroke="' + (e.occupied ? BUSY : FREE) + '" stroke-width="7"/>';
    }
    // 3) 车站方块（取自应答器坐标）
    var ba = (lastState && lastState.balises) || [];
    for (i = 0; i < ba.length; i++) {
      if (!ba[i].pos || (ba[i].tag !== 'station' && ba[i].tag !== 'stop')) continue;
      var cx = tx(ba[i].pos), cy = ty(ba[i].pos);
      s += '<rect x="' + (cx - 9) + '" y="' + (cy - 9) + '" width="18" height="18"'
        + ' fill="' + STATION + '" stroke="' + K + '" stroke-width="4"/>';
      s += '<text x="' + (cx + 20) + '" y="' + (cy + 5) + '" fill="' + K + '"'
        + ' font-size="13" font-weight="900" font-family="' + 'Consolas,monospace' + '">'
        + esc(ba[i].name) + '</text>';
    }
    s += '</svg>';

    $('#track').querySelectorAll('svg').forEach(function (n) { n.remove(); });
    $('#track').insertAdjacentHTML('beforeend', s);
    svgEl = $('#track').querySelector('svg');
    if (!viewInit) { fit(); viewInit = true; } else { applyView(); }
  }

  /* ================= 列表渲染 ================= */
  var TAGCLS = { station: 'info', limit: 'warn', stop: 'bad', spawn: 'ok', property: 'violet' };
  var CATDOT = { switch: 'warn', drive: 'violet', ma: 'info', tims: 'bad', mode: 'ok', graph: 'info', balise: 'violet' };
  var empty = function (n) { return '<tr><td colspan="' + n + '" class="empty">暂无数据</td></tr>'; };

  function trainRow(t) {
    var dotCls = t.intact ? 'ok' : 'bad';
    var stateText = (t.state && t.state !== '-') ? t.state : '—';
    var ma;
    if (t.eoa < 0) {
      ma = '<span class="mono">—</span>';
    } else {
      var c = t.eoa < 25 ? 'bad' : (t.eoa < 80 ? 'warn' : 'ok');
      ma = '<span class="pill ' + c + '">' + t.eoa.toFixed(0) + 'm</span>';
    }
    return '<tr>'
      + '<td><div class="driver"><span class="avatar">' + esc((t.driver || '?').slice(0, 1)) + '</span>'
      + esc(t.driver) + '</div></td>'
      + '<td><span class="pill ghost">' + esc(t.notch) + '</span></td>'
      + '<td><span class="speed">' + t.speedKmh.toFixed(0) + '</span> <span class="mono">km/h</span></td>'
      + '<td><span class="pill ghost">' + esc(t.mode) + '</span></td>'
      + '<td><span class="dot ' + dotCls + '"></span><span class="mono">' + esc(stateText) + '</span></td>'
      + '<td>' + ma + '</td></tr>';
  }

  function switchRow(s) {
    return '<tr><td><span class="mono">' + esc(s.id) + '</span></td>'
      + '<td><span class="pill ghost">' + s.positions + ' 段</span></td>'
      + '<td style="text-align:right"><button class="btn primary small" data-sw="'
      + esc(s.id) + '">转换</button></td></tr>';
  }

  function baliseRow(b) {
    return '<tr><td><span class="pill ' + (TAGCLS[b.tag] || 'ghost') + '">' + esc(b.tag) + '</span></td>'
      + '<td>' + esc(b.name) + '</td></tr>';
  }

  function eventRow(e) {
    return '<div class="ev"><time>' + esc(e.time) + '</time>'
      + '<span class="dot ' + (CATDOT[e.category] || 'info') + '"></span>'
      + '<span>' + esc(e.message) + '</span></div>';
  }

  /* ================= 渲染（分区块判脏，未变化不动 DOM） ================= */
  var sig = { graph: '', track: '', trains: '', switches: '', balises: '', events: '' };
  var lastState = null, lastEvents = [], wasOk = true;

  function render() {
    var st = lastState;
    if (st) {
      var g = st.graph || { nodes: 0, edges: 0 };
      var gs = g.nodes + '/' + g.edges;
      if (gs !== sig.graph) {
        sig.graph = gs;
        $('#graph').textContent = g.nodes + ' 节点 / ' + g.edges + ' 边';
      }

      var track = st.track || [];
      var ts = JSON.stringify(track);
      if (ts !== sig.track) {
        sig.track = ts;
        drawTrack(track);
      }

      var tr = st.trains || [];
      var trs = JSON.stringify(tr);
      if (trs !== sig.trains) {
        sig.trains = trs;
        $('#n-trains').textContent = tr.length;
        $('#trains').innerHTML = tr.length ? tr.map(trainRow).join('') : empty(6);
      }

      var sw = st.switches || [];
      var sws = JSON.stringify(sw);
      if (sws !== sig.switches) {
        sig.switches = sws;
        $('#n-switches').textContent = sw.length;
        $('#switches').innerHTML = sw.length ? sw.map(switchRow).join('') : empty(3);
      }

      var ba = st.balises || [];
      var bas = JSON.stringify(ba);
      if (bas !== sig.balises) {
        sig.balises = bas;
        $('#n-balises').textContent = ba.length;
        $('#balises').innerHTML = ba.length ? ba.map(baliseRow).join('') : empty(2);
      }
    }

    var es = JSON.stringify(lastEvents);
    if (es !== sig.events) {
      sig.events = es;
      $('#events').innerHTML = lastEvents.length
        ? lastEvents.map(eventRow).join('')
        : '<div class="empty">暂无事件</div>';
    }
  }

  /** 强制全量重绘（换主题时用，SVG 颜色需要跟着变） */
  function invalidateAll() {
    sig.graph = sig.track = sig.trains = sig.switches = sig.balises = sig.events = '';
    render();
  }

  /* ================= 轮询刷新 ================= */
  function refresh() {
    fetch('/api/state' + q)
      .then(function (r) { return r.json(); })
      .then(function (st) {
        lastState = st;
        if (!wasOk) { wasOk = true; toast('已重新连接调度服务', 'success'); }
        return fetch('/api/events' + q).then(function (r) { return r.json(); });
      })
      .then(function (ev) {
        lastEvents = ev || [];
        render();
      })
      .catch(function (err) {
        if (wasOk) { wasOk = false; toast('连接调度服务失败：' + err, 'error'); }
        $('#events').innerHTML = '<div class="empty" style="color:var(--danger)">连接失败：'
          + esc(err) + '</div>';
      });
  }

  /* 事件委托：即使表格刚被重绘，点击也能拿到当前的按钮 */
  $('#switches').addEventListener('click', function (e) {
    var btn = e.target.closest('button[data-sw]');
    if (btn) { MetroRail.switchToggle(btn.getAttribute('data-sw')); }
  });

  /* ================= 对外接口 ================= */
  window.MetroRail = {
    switchToggle: function (name) {
      fetch('/api/switch' + q + '&name=' + encodeURIComponent(name), { method: 'POST' })
        .then(function (r) { return r.json(); })
        .then(function (j) { toast('道岔 ' + name + ' 已转换（' + j.changed + ' 段）', 'success'); })
        .catch(function (err) { toast('道岔转换失败：' + err, 'error'); })
        .then(function () { invalidateAll(); refresh(); });
    },
    refresh: refresh,
    redraw: invalidateAll,
    toast: toast
  };

  $('#fit').onclick = fit;
  $('#zin').onclick = function () { var b = $('#track').getBoundingClientRect(); zoomAt(b.width / 2, b.height / 2, 1.2); };
  $('#zout').onclick = function () { var b = $('#track').getBoundingClientRect(); zoomAt(b.width / 2, b.height / 2, 1 / 1.2); };
  window.addEventListener('resize', applyView);

  refresh();
  setInterval(function () { if (!dragging) refresh(); }, REFRESH_MS);
})();
