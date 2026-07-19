import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    <div class="min-h-dvh bg-gray-50 text-gray-900">
      <header class="border-b border-gray-200 bg-white">
        <div class="mx-auto max-w-md px-4 py-3">
          <span class="text-sm font-bold tracking-tight text-indigo-600">통합 인증 데모</span>
        </div>
      </header>
      <main class="px-4 py-8">
        <router-outlet />
      </main>
    </div>
  `,
})
export class App {}
