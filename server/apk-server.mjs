import http from 'http';
import fs from 'fs';
import path from 'path';

const PORT = 3000;
const APK_PATH = '/app/applet/app/build/outputs/apk/debug/app-debug.apk';

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const pathname = url.pathname;

  // Direct APK file download routes
  if (pathname === '/app-debug.apk' || pathname === '/AIS-Studio-App.apk' || pathname === '/download') {
    if (!fs.existsSync(APK_PATH)) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('APK file not found. Please compile the applet first.');
      return;
    }

    const stat = fs.statSync(APK_PATH);
    const range = req.headers.range;

    if (range) {
      const parts = range.replace(/bytes=/, "").split("-");
      const start = parseInt(parts[0], 10);
      const end = parts[1] ? parseInt(parts[1], 10) : stat.size - 1;
      const chunksize = (end - start) + 1;
      const file = fs.createReadStream(APK_PATH, { start, end });

      res.writeHead(206, {
        'Content-Range': `bytes ${start}-${end}/${stat.size}`,
        'Accept-Ranges': 'bytes',
        'Content-Length': chunksize,
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="AIS-Studio-App.apk"',
      });
      file.pipe(res);
    } else {
      res.writeHead(200, {
        'Content-Length': stat.size,
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="AIS-Studio-App.apk"',
        'Accept-Ranges': 'bytes',
        'Cache-Control': 'no-cache',
      });
      fs.createReadStream(APK_PATH).pipe(res);
    }
    return;
  }

  // Health check
  if (pathname === '/healthz' || pathname === '/ping') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('OK');
    return;
  }

  // Beautiful Direct Download Landing Page
  let fileSizeMB = '29 MB';
  try {
    if (fs.existsSync(APK_PATH)) {
      fileSizeMB = (fs.statSync(APK_PATH).size / (1024 * 1024)).toFixed(1) + ' MB';
    }
  } catch (e) {}

  const html = `<!DOCTYPE html>
<html lang="ur" dir="ltr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Download APK - Google AI Studio Android App</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700;800&display=swap" rel="stylesheet">
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif;
      background: radial-gradient(circle at 50% 0%, #1e1b4b 0%, #09090b 100%);
      color: #f4f4f5;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px;
    }
    .card {
      background: rgba(24, 24, 27, 0.85);
      border: 1px solid rgba(255, 255, 255, 0.1);
      backdrop-filter: blur(20px);
      border-radius: 24px;
      padding: 40px 32px;
      max-width: 520px;
      width: 100%;
      text-align: center;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5), 0 0 40px rgba(99, 102, 241, 0.15);
    }
    .app-icon {
      width: 80px;
      height: 80px;
      border-radius: 20px;
      background: linear-gradient(135deg, #6366f1 0%, #a855f7 50%, #ec4899 100%);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 38px;
      box-shadow: 0 10px 25px rgba(99, 102, 241, 0.4);
      margin-bottom: 20px;
      animation: bounce 2s infinite ease-in-out;
    }
    @keyframes bounce {
      0%, 100% { transform: translateY(0); }
      50% { transform: translateY(-6px); }
    }
    h1 {
      font-size: 26px;
      font-weight: 800;
      color: #ffffff;
      margin-bottom: 8px;
      letter-spacing: -0.5px;
    }
    .subtitle {
      font-size: 14px;
      color: #a1a1aa;
      margin-bottom: 24px;
      line-height: 1.5;
    }
    .badge-container {
      display: flex;
      gap: 8px;
      justify-content: center;
      flex-wrap: wrap;
      margin-bottom: 28px;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 600;
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.08);
      color: #e4e4e7;
    }
    .badge.green {
      background: rgba(34, 197, 94, 0.12);
      border-color: rgba(34, 197, 94, 0.3);
      color: #4ade80;
    }
    .badge.blue {
      background: rgba(59, 130, 246, 0.12);
      border-color: rgba(59, 130, 246, 0.3);
      color: #60a5fa;
    }
    .download-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      width: 100%;
      background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
      color: #ffffff;
      padding: 18px 24px;
      border-radius: 16px;
      font-size: 18px;
      font-weight: 700;
      text-decoration: none;
      box-shadow: 0 10px 25px rgba(79, 70, 229, 0.4);
      transition: all 0.2s ease;
      cursor: pointer;
      border: none;
    }
    .download-btn:hover {
      transform: translateY(-2px);
      box-shadow: 0 14px 30px rgba(79, 70, 229, 0.55);
      background: linear-gradient(135deg, #4338ca 0%, #6d28d9 100%);
    }
    .download-btn:active {
      transform: translateY(1px);
    }
    .download-note {
      margin-top: 14px;
      font-size: 13px;
      color: #71717a;
    }
    .features {
      margin-top: 28px;
      padding-top: 24px;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
      text-align: left;
    }
    .feature-item {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 13px;
      color: #d4d4d8;
      margin-bottom: 10px;
    }
    .feature-item svg {
      flex-shrink: 0;
      color: #22c55e;
    }
  </style>
  <script>
    // Auto-trigger direct download on page load
    window.addEventListener('DOMContentLoaded', () => {
      setTimeout(() => {
        const link = document.createElement('a');
        link.href = '/app-debug.apk';
        link.download = 'AIS-Studio-App.apk';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      }, 800);
    });
  </script>
</head>
<body>
  <div class="card">
    <div class="app-icon">🤖</div>
    <h1>Direct APK Download</h1>
    <p class="subtitle">Google AI Studio Complete Android Native App<br>Fast Nearest Server (asia-southeast1 / Singapore)</p>

    <div class="badge-container">
      <div class="badge green">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg>
        No Robot / Captcha Removed
      </div>
      <div class="badge blue">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg>
        Direct Simple Login Enabled
      </div>
      <div class="badge">
        📦 Size: ${fileSizeMB}
      </div>
    </div>

    <a href="/app-debug.apk" download="AIS-Studio-App.apk" class="download-btn" id="dlBtn">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
        <polyline points="7 10 12 15 17 10"></polyline>
        <line x1="12" y1="15" x2="12" y2="3"></line>
      </svg>
      Download APK Now (${fileSizeMB})
    </a>

    <p class="download-note">Download automatically shuru ho jaye ga. Agar na ho to upar button dabayein.</p>

    <div class="features">
      <div class="feature-item">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg>
        <span><b>Direct Simple Login:</b> Email + Password se foran login karein, koi verification puzzle nahi.</span>
      </div>
      <div class="feature-item">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg>
        <span><b>Qareebi Server:</b> asia-southeast1 (Singapore) ultra low latency high speed download.</span>
      </div>
      <div class="feature-item">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg>
        <span><b>Complete Features:</b> Models, Prompts, Tuning, Live Playground, Token counter aur History.</span>
      </div>
    </div>
  </div>
</body>
</html>`;

  res.writeHead(200, {
    'Content-Type': 'text/html; charset=utf-8',
    'Content-Length': Buffer.byteLength(html),
  });
  res.end(html);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`APK Download Server running on http://0.0.0.0:${PORT}`);
});
