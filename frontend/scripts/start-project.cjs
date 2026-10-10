// One terminal owns both servers. No database/SMTP secrets are passed as arguments.
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const http = require('node:http');
const { parseEnv } = require('node:util');
const { spawn } = require('node:child_process');

const frontend = path.resolve(__dirname, '..');
const backend = path.resolve(frontend, '..', 'backend');
const localConfig = path.join(backend, '.env');
const children = new Set();
let stopping = false;

function readConfig() {
  if (!parseEnv) throw new Error('Cần Node.js 20.12 trở lên. Hãy cập nhật Node.js rồi chạy lại.');
  let local = {};
  if (fs.existsSync(localConfig)) {
    try { local = parseEnv(fs.readFileSync(localConfig, 'utf8')); }
    catch { throw new Error('Không đọc được backend/.env. Kiểm tra định dạng TEN_BIEN=gia_tri.'); }
  }
  const env = { ...local, ...process.env };
  const required = ['DB_PASSWORD'];
  const missing = required.filter((key) => !env[key]);
  if (missing.length) throw new Error(`Thiếu cấu hình trong backend/.env: ${missing.join(', ')}. Sao chép backend/.env.example thành backend/.env và điền mật khẩu database.`);
  return env;
}

async function assertFree(port) {
  for (const host of ['127.0.0.1', '::1']) {
    await new Promise((resolve, reject) => {
      const server = net.createServer();
      server.once('error', (error) => {
        if (error.code === 'EAFNOSUPPORT' || error.code === 'EADDRNOTAVAIL') resolve();
        else reject(new Error(`Cổng ${port} đang được dùng hoặc không được phép mở. Dừng phiên npm/BackendApplication cũ rồi chạy lại. Không tự động chuyển sang cổng khác.`));
      });
      server.listen({ port, host, exclusive: true }, () => server.close(resolve));
    });
  }
}

function launch(command, args, options, label) {
  const child = spawn(command, args, { ...options, stdio: 'inherit', windowsHide: true, detached: process.platform !== 'win32' });
  children.add(child);
  child.once('error', () => {
    console.error(`[GenZ] Không chạy được ${label}. Kiểm tra Java/Node và quyền chạy chương trình.`);
    void stop(1);
  });
  child.once('exit', (code) => {
    children.delete(child);
    if (!stopping) {
      console.error(`[GenZ] ${label} đã dừng (mã ${code ?? 'signal'}). Xem lỗi phía trên.`);
      void stop(code || 1);
    }
  });
  return child;
}

async function stop(code = 0) {
  if (stopping) return;
  stopping = true;
  console.log('\n[GenZ] Đang dừng backend và frontend của phiên này…');
  await Promise.all([...children].map((child) => new Promise((resolve) => {
    if (!child.pid) return resolve();
    if (process.platform === 'win32') {
      // Only terminate descendants of processes this runner created, never by port/name.
      const killer = spawn('taskkill.exe', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore', windowsHide: true });
      killer.once('exit', resolve); killer.once('error', resolve);
    } else {
      try { process.kill(-child.pid, 'SIGTERM'); } catch { /* already exited */ }
      resolve();
    }
  })));
  process.exit(code);
}

function backendReady() {
  return new Promise((resolve) => {
    const request = http.get('http://127.0.0.1:8080/api/v1/auth/health', { timeout: 1500 }, (response) => {
      let body = '';
      response.on('data', (chunk) => { if (body.length < 8192) body += chunk; });
      response.on('error', () => resolve(false));
      response.on('end', () => {
        try { const data = JSON.parse(body); resolve(response.statusCode === 200 && data.status === 'UP'); }
        catch { resolve(false); }
      });
    });
    request.on('timeout', () => request.destroy());
    request.on('error', () => resolve(false));
  });
}

async function main() {
  const config = readConfig();
  await assertFree(8080);
  await assertFree(3000);
  const temporary = path.join(backend, 'target', 'dev-tmp');
  fs.mkdirSync(temporary, { recursive: true });
  const backendEnv = {
    ...config,
    SPRING_PROFILES_ACTIVE: config.SPRING_PROFILES_ACTIVE || 'dev',
    AUTH_SECURE_COOKIE: config.AUTH_SECURE_COOKIE || 'false',
    FRONTEND_ORIGIN: 'http://localhost:3000',
    SERVER_PORT: '8080', SERVER_ADDRESS: '127.0.0.1',
    TEMP: temporary, TMP: temporary,
    // A short temporary path also avoids the Java 17 Windows socket path limit.
    JAVA_TOOL_OPTIONS: `${config.JAVA_TOOL_OPTIONS || ''} -Djava.io.tmpdir="${temporary}" -Dspring.devtools.restart.enabled=false`.trim(),
  };
  console.log('[GenZ] Đang khởi động backend và kết nối PostgreSQL…');
  if (process.platform === 'win32') {
    launch(process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', 'mvnw.cmd spring-boot:run'], { cwd: backend, env: backendEnv }, 'Backend');
  } else {
    launch('sh', ['./mvnw', 'spring-boot:run'], { cwd: backend, env: backendEnv }, 'Backend');
  }
  const deadline = Date.now() + 180000;
  while (!stopping && Date.now() < deadline) {
    if (await backendReady()) {
      console.log('[GenZ] Backend sẵn sàng. Đang mở http://localhost:3000/dang-nhap');
      // Do not copy the backend .env (database / SMTP credentials) to the frontend process.
      launch(process.execPath, [require.resolve('react-scripts/scripts/start.js')], {
        cwd: frontend,
        env: { ...process.env, PORT: '3000', HOST: '0.0.0.0', REACT_APP_API_BASE_URL: '/api/v1' },
      }, 'Frontend');
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  if (!stopping) throw new Error('Backend chưa sẵn sàng sau 3 phút. Kiểm tra PostgreSQL, cấu hình và lỗi khởi động phía trên.');
}

process.on('SIGINT', () => void stop(0));
process.on('SIGTERM', () => void stop(0));
main().catch((error) => { console.error(`[GenZ] ${error.message}`); void stop(1); });
