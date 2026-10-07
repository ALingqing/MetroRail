/* 预览用的 fetch 模拟层：让真实 app.js 在无服务器时也能跑 */
(function () {
  'use strict';

  /* ---------- 轨道网络（x,y,z；y 仅用于上下层示意） ---------- */
  var edges = [];
  function seg(a, b) { edges.push({ from: a, to: b }); }

  // 主线 z=0：x 0..26
  for (var x = 0; x < 26; x++) seg([x, 4, 0], [x + 1, 4, 0]);
  // 支线 z=8：x 8..18
  for (var x2 = 8; x2 < 18; x2++) seg([x2, 4, 8], [x2 + 1, 4, 8]);
  // 联络线：x=8 与 x=18 处各一条 0..8 的竖直段
  for (var z = 0; z < 8; z++) seg([8, 4, z], [8, 4, z + 1]);
  for (var z2 = 0; z2 < 8; z2++) seg([18, 4, z2], [18, 4, z2 + 1]);
  // 车辆段尽头线
  for (var x3 = 0; x3 > -5; x3--) seg([x3, 4, 0], [x3 - 1, 4, 0]);

  var occupied = {};
  ['3,0>4,0', '4,0>5,0', '5,0>6,0', '6,0>7,0', '8,6>8,7', '8,7>8,8'].forEach(function (k) { occupied[k] = true; });
  var key = function (a, b) { return a.join(',') + '>' + b.join(','); };

  var track = edges.map(function (e) {
    return { from: e.from, to: e.to, occupied: !!occupied[key(e.from, e.to)] };
  });

  var state = {
    graph: { world: 'world', nodes: 78, edges: edges.length },
    track: track,
    trains: [
      { driver: '阿清', train: '阿清', vehicle: 'default', speedKmh: 38, notch: 'p2', reverser: 'forward',
        mode: 'shadow', state: 'SB', eoa: 126, intact: true },
      { driver: '白桦林', train: '白桦林', vehicle: 'express', speedKmh: 12, notch: 'b3', reverser: 'forward',
        mode: 'isolate', state: '-', eoa: -1, intact: true },
      { driver: '方块旅人', train: '方块旅人', vehicle: 'default', speedKmh: 0, notch: 'eb', reverser: 'neutral',
        mode: 'enforce', state: 'PT', eoa: 8, intact: false }
    ],
    switches: [
      { id: 'north-yard', positions: 4, location: 'world:8:4:4' },
      { id: 'south-yard', positions: 2, location: 'world:18:4:2' }
    ],
    balises: [
      { tag: 'station', name: '中央车站', id: 'world:5:4:0', pos: [5, 4, 0] },
      { tag: 'station', name: '东环站', id: 'world:22:4:0', pos: [22, 4, 0] },
      { tag: 'station', name: '车辆段', id: 'world:13:4:8', pos: [13, 4, 8] },
      { tag: 'limit', name: '0.4', id: 'world:10:4:0' },
      { tag: 'stop', name: '终点', id: 'world:-5:4:0' },
      { tag: 'spawn', name: '出库', id: 'world:-3:4:0' }
    ]
  };

  function timeOffset(sec) {
    return new Date(Date.now() + sec * 1000).toTimeString().slice(0, 8);
  }

  var events = [
    { time: timeOffset(-3), category: 'switch', message: 'web 转换道岔 north-yard' },
    { time: timeOffset(-11), category: 'drive', message: '阿清 接管矿车（车型 default）' },
    { time: timeOffset(-26), category: 'ma', message: '阿清 申请 3 段' },
    { time: timeOffset(-48), category: 'tims', message: '方块旅人 完整性丢失（missing）' },
    { time: timeOffset(-70), category: 'mode', message: '方块旅人 切换模式 enforce' },
    { time: timeOffset(-104), category: 'graph', message: '阿清 重建轨道图 78 节点' },
    { time: timeOffset(-150), category: 'balise', message: '阿清 登记 [station] world:22:4:0' }
  ];

  /* ---------- 拦截 fetch ---------- */
  var realFetch = window.fetch ? window.fetch.bind(window) : null;
  window.fetch = function (url, options) {
    url = String(url);
    if (url.indexOf('/api/state') === 0) {
      // 让一辆列车速度轻微波动，模拟实时
      state.trains[0].speedKmh = Math.max(0, Math.round(38 + Math.sin(Date.now() / 1500) * 12));
      return Promise.resolve(json(state));
    }
    if (url.indexOf('/api/events') === 0) {
      return Promise.resolve(json(events));
    }
    if (url.indexOf('/api/switch') === 0) {
      var m = /name=([^&]+)/.exec(url);
      var name = m ? decodeURIComponent(m[1]) : '?';
      events.unshift({ time: timeOffset(0), category: 'switch', message: 'web 转换道岔 ' + name });
      return Promise.resolve(json({ switch: name, changed: 4 }));
    }
    return realFetch ? realFetch(url, options) : Promise.resolve(json({}));
  };

  function json(body) {
    return { ok: true, status: 200, json: function () { return Promise.resolve(body); } };
  }
})();
