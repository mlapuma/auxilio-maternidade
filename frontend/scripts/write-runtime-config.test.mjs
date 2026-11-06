import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeApiBaseUrl } from './write-runtime-config.mjs';

test('accepts the homologation HTTPS origin and removes the trailing slash', () => {
  assert.equal(normalizeApiBaseUrl('https://auxilio-maternidade-hml-mlapuma-api.onrender.com/'),
    'https://auxilio-maternidade-hml-mlapuma-api.onrender.com');
});
test('rejects credentials and malformed destinations in the public config', () => {
  for (const value of ['https://user:secret@example.com', 'https://example.com/api',
    'https://example.com?token=secret', 'https://example.com/#fragment', 'http://example.com', 'javascript:alert(1)']) {
    assert.throws(() => normalizeApiBaseUrl(value));
  }
});
test('supports explicit same-origin deployments and local development', () => {
  assert.equal(normalizeApiBaseUrl('same-origin'), '');
  assert.equal(normalizeApiBaseUrl('http://localhost:8080'), 'http://localhost:8080');
});
