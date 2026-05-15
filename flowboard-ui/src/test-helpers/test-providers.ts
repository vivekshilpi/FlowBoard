import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider, importProvidersFrom } from '@angular/core';
import {
  ActivatedRoute,
  Params,
  convertToParamMap,
  provideRouter
} from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogModule } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { of } from 'rxjs';

export function createHttpTestingProviders(): Provider[] {
  return [
    provideHttpClient(),
    provideHttpClientTesting()
  ] as unknown as Provider[];
}

export function createComponentTestingProviders(options?: {
  routeParams?: Params;
  queryParams?: Params;
  data?: Record<string, unknown>;
  includeStore?: boolean;
}): Array<Provider | EnvironmentProviders> {
  const providers: Array<Provider | EnvironmentProviders> = [
    ...createHttpTestingProviders(),
    provideRouter([]),
    provideNoopAnimations(),
    importProvidersFrom(MatDialogModule),
    {
      provide: ActivatedRoute,
      useValue: {
        snapshot: {
          paramMap: convertToParamMap(options?.routeParams ?? {}),
          queryParamMap: convertToParamMap(options?.queryParams ?? {}),
          data: options?.data ?? {}
        }
      }
    }
  ];

  if (options?.includeStore) {
    providers.push({
      provide: Store,
      useValue: {
        select: () => of([]),
        dispatch: () => undefined
      }
    });
  }

  return providers;
}
