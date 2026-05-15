import { Component, OnInit, OnDestroy, HostListener, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule, NavigationEnd } from '@angular/router';
import { ViewportScroller } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { Subject } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';
import { ThemeService } from '../../core/services/theme.service';

interface Feature {
  icon: string;
  title: string;
  description: string;
  emoji?: string;
}

interface Testimonial {
  name: string;
  role: string;
  company: string;
  text: string;
  avatar: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatButtonModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  readonly themeService = inject(ThemeService);
  scrolled = false;

  features: Feature[] = [
    {
      emoji: '📊',
      icon: 'dashboard',
      title: 'Kanban Boards',
      description: 'Drag-and-drop task management with unlimited boards.'
    },
    {
      emoji: '🤝',
      icon: 'people',
      title: 'Real-Time Collaboration',
      description: 'Work together seamlessly with live updates and comments.'
    },
    {
      emoji: '🏗️',
      icon: 'architecture',
      title: 'Microservices Architecture',
      description: 'Enterprise-grade stability and infinite scalability.'
    },
    {
      emoji: '📈',
      icon: 'trending_up',
      title: 'Productivity Analytics',
      description: 'Gain deep insights into team performance metrics.'
    },
    {
      emoji: '🔒',
      icon: 'security',
      title: 'Secure Authentication',
      description: 'Enterprise-level security with role-based access.'
    },
    {
      emoji: '🔔',
      icon: 'notifications',
      title: 'Smart Notifications',
      description: 'Never miss important updates and deadlines.'
    }
  ];

  testimonials: Testimonial[] = [
    {
      name: 'Sarah Chen',
      role: 'Product Manager',
      company: 'TechCorp',
      text: 'FlowBoard transformed how our team collaborates. We shipped faster than ever before.',
      avatar: 'SC'
    },
    {
      name: 'Marcus Johnson',
      role: 'Engineering Lead',
      company: 'StartupXYZ',
      text: 'The microservices architecture gives us confidence that our system will scale with us.',
      avatar: 'MJ'
    },
    {
      name: 'Elena Rodriguez',
      role: 'CTO',
      company: 'Global Ventures',
      text: 'Switching to FlowBoard was the best decision. Our productivity increased by 40%.',
      avatar: 'ER'
    }
  ];

  constructor(private router: Router, private scroller: ViewportScroller) {}

  ngOnInit(): void {
    this.router.events
      .pipe(
        filter(event => event instanceof NavigationEnd),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        this.handleAnchorScroll();
      });

    this.handleAnchorScroll();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  @HostListener('window:scroll')
  onScroll(): void {
    this.scrolled = window.scrollY > 50;
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }

  private handleAnchorScroll(): void {
    const fragment = this.router.parseUrl(this.router.url).fragment;
    
    if (fragment) {
      setTimeout(() => {
        const element = document.getElementById(fragment);
        if (element) {
          element.scrollIntoView({ behavior: 'smooth' });
        }
      }, 0);
    }
  }
}
