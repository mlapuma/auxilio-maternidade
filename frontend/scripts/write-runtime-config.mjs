import { writeFileSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';

export function normalizeApiBaseUrl(value) {
  if (value === 'same-origin') return '';
  const input = (value ?? 'https://auxilio-maternidade.onrender.com').trim();
  const url = new URL(input);
  const local = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if ((url.protocol !== 'https:' && !(local && url.protocol === 'http:'))
      || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('API_BASE_URL must be an HTTPS origin without credentials, path, query or fragment.');
  }
  return url.origin;
}

export function writeRuntimeConfig(value = process.env.API_BASE_URL) {
  const config = { apiBaseUrl: normalizeApiBaseUrl(value) };
  writeFileSync(fileURLToPath(new URL('../public/config.js', import.meta.url)),
    `window.__AUXILIO_CONFIG__ = ${JSON.stringify(config)};\n`, 'utf8');
  console.log(`API destination: ${config.apiBaseUrl || 'same origin'}`);
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) writeRuntimeConfig();
