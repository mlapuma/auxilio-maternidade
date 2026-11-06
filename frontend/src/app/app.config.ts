import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors, HttpInterceptorFn } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { resolveApiBaseUrl } from './api-base-url';

// Local development uses the Angular proxy; static hosting uses the Render API.
const apiInterceptor: HttpInterceptorFn = (req, next) => {
  const config = (window as Window & { __AUXILIO_CONFIG__?: { apiBaseUrl?: string } }).__AUXILIO_CONFIG__;
  const apiBaseUrl = resolveApiBaseUrl(window.location.hostname, config?.apiBaseUrl);
  if (!req.url.startsWith('/api/') || !apiBaseUrl) return next(req);
  return next(req.clone({ url: apiBaseUrl + req.url }));
};

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([apiInterceptor])),
    provideRouter(routes)
  ]
};
