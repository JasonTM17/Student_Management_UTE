import type { ScreenName, UserRole } from './routes';

export interface MobileNavigationOptions {
  thesisTopicId?: string;
}

export interface MobileNavigation {
  navigate(route: ScreenName, options?: MobileNavigationOptions): void;
  enterPreview(): void;
  completeSignIn(role: UserRole): void;
  goBack(): void;
  signOut(): void;
  switchRole(role: UserRole): void;
}

export interface MobileScreenProps {
  navigation: MobileNavigation;
  role: UserRole;
  selectedThesisTopicId: string | null;
}
