import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-route-fallback',
  standalone: true,
  template: ''
})
export class RouteFallbackComponent implements OnInit {
  private router = inject(Router);
  private auth = inject(AuthService);

  ngOnInit(): void {
    this.router.navigateByUrl(this.auth.isLoggedIn() ? '/dashboard' : '/');
  }
}
