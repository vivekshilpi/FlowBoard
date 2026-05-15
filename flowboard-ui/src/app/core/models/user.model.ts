export interface AuthResponse {
  message: string;
  token: string | null;
}

export interface UserProfile {
  id: number;
  fullName: string;
  username: string;
  email: string;
  avatarUrl: string | null;
  bio: string | null;
  themePreference?: 'light' | 'dark' | null;
  role: 'MEMBER' | 'PLATFORM_ADMIN';
  active: boolean;
  emailVerified: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  username: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UpdateProfileRequest {
  fullname: string;
  username: string;
  avatarUrl?: string;
  bio?: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

export interface UpdateThemePreferenceRequest {
  themePreference: 'light' | 'dark';
}

export interface ProfileActivityLog {
  id: number;
  action: string;
  summary: string;
  createdAt: string;
}
