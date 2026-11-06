export const DEFAULT_API_BASE_URL = 'https://auxilio-maternidade.onrender.com';

export function resolveApiBaseUrl(hostname: string, configured?: string): string {
  if (['localhost', '127.0.0.1', '[::1]'].includes(hostname)) return '';
  return (configured ?? DEFAULT_API_BASE_URL).replace(/\/$/, '');
}
