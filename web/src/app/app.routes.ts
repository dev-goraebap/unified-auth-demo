import { Routes } from '@angular/router';
import { authGuard } from './shared/session/auth-guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'accounts' },
  { path: 'login', loadComponent: () => import('./pages/login/login-page').then((m) => m.LoginPage) },
  { path: 'signup', loadComponent: () => import('./pages/signup/signup-page').then((m) => m.SignupPage) },
  {
    path: 'auth/callback/:provider',
    loadComponent: () => import('./pages/oauth-callback/oauth-callback-page').then((m) => m.OAuthCallbackPage),
  },
  {
    path: 'accounts',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/accounts/accounts-page').then((m) => m.AccountsPage),
  },
  { path: '**', redirectTo: 'accounts' },
];
