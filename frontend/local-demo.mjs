import { readFile } from 'node:fs/promises';

const roles = { STUDENT: { username: 'lihe', label: '学生入院' }, TEACHER: { username: 'lincheng', label: '教师入院' } };
const loopback = host => ['127.0.0.1', 'localhost', '[::1]', '::1', '::ffff:127.0.0.1'].includes(host);

// Private passwords are read on the dev server only; never imported into client bundles.
export function localDemo(gateway) {
  return {
    name: 'hanwen-local-demo',
    apply: (_config, env) => env.command === 'serve' && !env.isPreview,
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const path = req.url?.split('?')[0];
        if (!['/__local-demo/accounts', '/__local-demo/login'].includes(path)) return next();
        const reply = (status, body) => {
          res.statusCode = status;
          res.setHeader('Content-Type', 'application/json; charset=utf-8');
          res.setHeader('Cache-Control', 'no-store');
          res.end(JSON.stringify(body));
        };
        try {
          const host = new URL(`http://${req.headers.host || ''}`);
          const target = new URL(gateway);
          if (!loopback(host.hostname) || !loopback(req.socket.remoteAddress) || !loopback(target.hostname))
            return reply(403, { message: '快捷登录仅限本机使用' });
          // Reject cross-origin requests, including local DNS rebinding and form submissions.
          const origin = req.headers.origin;
          if ((origin && origin !== host.origin) || (req.headers['sec-fetch-site'] && !['same-origin', 'none'].includes(req.headers['sec-fetch-site'])))
            return reply(403, { message: '请从本机登录页面进入' });
          if (path === '/__local-demo/accounts' && req.method === 'GET') {
            const accounts = JSON.parse(await readFile(new URL('../.local/demo-accounts.json', import.meta.url), 'utf8'));
            return reply(200, Object.entries(roles).filter(([, v]) => accounts.some(a => a.username === v.username)).map(([role, v]) => ({ role, ...v })));
          }
          if (path !== '/__local-demo/login' || req.method !== 'POST') return reply(405, { message: '请求方式不支持' });
          if (origin !== host.origin || !req.headers['content-type']?.startsWith('application/json'))
            return reply(403, { message: '请从本机登录页面进入' });
          let body = '';
          for await (const chunk of req) {
            body += chunk.toString();
            if (Buffer.byteLength(body) > 1024) return reply(413, { message: '请求过大' });
          }
          let role;
          try { role = JSON.parse(body).role; } catch { return reply(400, { message: '请选择学生或教师' }); }
          if (!Object.hasOwn(roles, role)) return reply(400, { message: '请选择学生或教师' });
          const accounts = JSON.parse(await readFile(new URL('../.local/demo-accounts.json', import.meta.url), 'utf8'));
          const account = accounts.find(a => a.username === roles[role].username);
          if (!account?.password) return reply(503, { message: '本机演示账号暂不可用，请使用普通登录' });
          const response = await fetch(new URL('/api/auth/login', target), {
            method: 'POST', redirect: 'error', signal: AbortSignal.timeout(15000),
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: account.username, password: account.password }),
          });
          const result = await response.json();
          const cookies = response.headers.getSetCookie();
          if (cookies.length) res.setHeader('Set-Cookie', cookies);
          reply(response.status, result);
        } catch {
          reply(503, { message: '快捷登录暂不可用，请稍后重试或使用普通登录' });
        }
      });
    },
  };
}
