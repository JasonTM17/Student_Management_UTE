import type { ScreenName, UserRole } from './routes';

export interface MobileNavigation {
  navigate(route: ScreenName): void;
  enterPreview(): void;
  goBack(): void;
  signOut(): void;
  switchRole(role: UserRole): void;
}

export interface MobileScreenProps {
  navigation: MobileNavigation;
  role: UserRole;
}
