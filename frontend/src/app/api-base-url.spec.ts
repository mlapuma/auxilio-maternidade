import { DEFAULT_API_BASE_URL, resolveApiBaseUrl } from './api-base-url';

describe('API por ambiente', () => {
  it('mantém localhost no proxy mesmo com configuração pública', () => {
    for (const host of ['localhost', '127.0.0.1', '[::1]']) {
      expect(resolveApiBaseUrl(host, 'https://example.com')).toBe('');
    }
  });
  it('envia a homologação para a API configurada e preserva o comportamento existente', () => {
    expect(resolveApiBaseUrl('hml.example.com', 'https://api-hml.example.com/')).toBe('https://api-hml.example.com');
    expect(resolveApiBaseUrl('existing.example.com')).toBe(DEFAULT_API_BASE_URL);
    expect(resolveApiBaseUrl('same-origin.example.com', '')).toBe('');
  });
});
