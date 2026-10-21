import { HttpInterceptorFn } from '@angular/common/http';

/**
 * O Render Static Site nao faz proxy de /api para outro servico.
 * Em desenvolvimento, manter URLs relativas para usar o proxy local.
 */
export const apiBaseUrlInterceptor: HttpInterceptorFn = (request, next) => {
  const hostname = globalThis.location?.hostname ?? '';
  const isProductionSite = hostname === 'auxiliomaternidade.financasemdiabrasil.com.br'
    || hostname === 'auxilio-maternidade-frontend.onrender.com';

  if (!isProductionSite || !request.url.startsWith('/api/')) {
    return next(request);
  }

  return next(request.clone({
    url: 'https://auxilio-maternidade.onrender.com' + request.url,
  }));
};
