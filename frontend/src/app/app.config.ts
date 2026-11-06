import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors, HttpInterceptorFn } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';

// Local development uses the Angular proxy; static hosting uses the Render API.
const apiInterceptor: HttpInterceptorFn = (req, next) => {
  const localHosts = ['localhost', '127.0.0.1', '[::1]'];
  if (!req.url.startsWith('/api/') || localHosts.includes(window.location.hostname)) return next(req);
  return next(req.clone({ url: 'https://auxilio-maternidade.onrender.com' + req.url }));
};

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([apiInterceptor])),
    provideRouter(routes)
  ]
};
