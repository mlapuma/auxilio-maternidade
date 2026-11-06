import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class WhatsAppNavigation {
  abrir(url: string): void {
    window.location.assign(url);
  }
}
