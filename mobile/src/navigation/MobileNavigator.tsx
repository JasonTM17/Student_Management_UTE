import { useEffect, useState } from 'react';
import { Linking, SafeAreaView, StatusBar, StyleSheet, View } from 'react-native';

import { apiClient, campusApi } from '../api/client';
import { BottomNavigation } from '../components/BottomNavigation';
import { MenuPanel } from '../components/MenuPanel';
import { tokens } from '../design/tokens';
import { screenComponents } from '../screens';
import { canAccessScreen, type ScreenName, type UserRole } from './routes';
import type { MobileNavigation } from './types';

const roleHome: Record<UserRole, ScreenName> = {
  student: 'dashboard.student',
  lecturer: 'lecturer.dashboard',
  admin: 'admin.dashboard',
};

type SessionKind = 'signedOut' | 'preview' | 'authenticated';

export function MobileNavigator() {
  const [route, setRoute] = useState<ScreenName>('auth.signIn');
  const [role, setRole] = useState<UserRole>('student');
  const [sessionKind, setSessionKind] = useState<SessionKind>('signedOut');
  const [menuOpen, setMenuOpen] = useState(false);
  const [selectedThesisTopicId, setSelectedThesisTopicId] = useState<string | null>(null);
  const [authToken, setAuthToken] = useState<string | null>(null);
  const isPreviewSession = sessionKind === 'preview';
  const isAuthenticated = sessionKind === 'authenticated';
  const hasActiveSession = isPreviewSession || isAuthenticated;

  const navigation: MobileNavigation = {
    navigate(nextRoute, options) {
      if (!hasActiveSession && !nextRoute.startsWith('auth.')) {
        return;
      }
      if (!canAccessScreen(role, nextRoute)) {
        setRoute(roleHome[role]);
        setMenuOpen(false);
        return;
      }
      if (options?.thesisTopicId) {
        setSelectedThesisTopicId(options.thesisTopicId);
      }
      if (nextRoute !== 'auth.verifyEmail' && nextRoute !== 'auth.resetPassword') {
        setAuthToken(null);
      }
      setRoute(nextRoute);
      setMenuOpen(false);
    },
    enterPreview() {
      if (route !== 'auth.signIn' || apiClient.mode !== 'preview') {
        return;
      }
      setSessionKind('preview');
      setSelectedThesisTopicId(null);
      setRoute(roleHome[role]);
      setMenuOpen(false);
    },
    completeSignIn(nextRole) {
      if (route !== 'auth.signIn' || apiClient.mode !== 'live') {
        return;
      }
      setSessionKind('authenticated');
      setRole(nextRole);
      setSelectedThesisTopicId(null);
      setRoute(roleHome[nextRole]);
      setMenuOpen(false);
    },
    goBack() {
      setRoute(hasActiveSession ? roleHome[role] : 'auth.signIn');
      setMenuOpen(false);
    },
    signOut() {
      if (apiClient.mode === 'live') {
        void campusApi.logout().catch(() => undefined).finally(() => apiClient.clearAccessToken());
      } else {
        apiClient.clearAccessToken();
      }
      setSessionKind('signedOut');
      setRole('student');
      setSelectedThesisTopicId(null);
      setAuthToken(null);
      setRoute('auth.signIn');
      setMenuOpen(false);
    },
    switchRole(nextRole) {
      if (!isPreviewSession) {
        return;
      }
      setRole(nextRole);
      setSelectedThesisTopicId(null);
      setRoute(roleHome[nextRole]);
      setMenuOpen(false);
    },
  };

  useEffect(() => {
    const handleUrl = (url: string | null) => {
      if (!url) return;
      try {
        // SMTP mail uses a fragment so the raw challenge never reaches
        // intermediary access logs; accept query tokens as a manual/deep-link
        // fallback too.
        const [urlWithoutFragment, rawFragment = ''] = url.split('#', 2);
        const [rawPath, rawQuery = ''] = urlWithoutFragment.split('?', 2);
        const path = rawPath.toLowerCase();
        const tokenPart = `${rawQuery}&${rawFragment}`
          .split('&')
          .find((part) => part.startsWith('token='));
        const token = tokenPart ? decodeURIComponent(tokenPart.slice('token='.length)) : null;
        if (token) setAuthToken(token);
        if (path.includes('reset')) {
          setRoute('auth.resetPassword');
        } else if (path.includes('verify') || path.includes('confirm')) {
          setRoute('auth.verifyEmail');
        }
      } catch {
        // A malformed deep link falls back to manual token entry.
      }
    };
    void Linking.getInitialURL().then(handleUrl);
    const subscription = Linking.addEventListener('url', ({ url }) => handleUrl(url));
    return () => subscription.remove();
  }, []);

  const ScreenComponent = screenComponents[route];

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={tokens.colors.background} />
      <View style={styles.container}>
        <View
          importantForAccessibility={menuOpen ? 'no-hide-descendants' : 'auto'}
          style={styles.content}
        >
          <ScreenComponent
            navigation={navigation}
            role={role}
            selectedThesisTopicId={selectedThesisTopicId}
            authToken={authToken}
          />
          {hasActiveSession ? (
            <BottomNavigation
              activeRoute={route}
              role={role}
              onNavigate={navigation.navigate}
              onMenu={() => setMenuOpen(true)}
            />
          ) : null}
        </View>
        {menuOpen ? (
          <MenuPanel
            activeRoute={route}
            role={role}
            onClose={() => setMenuOpen(false)}
            onNavigate={navigation.navigate}
            onSwitchRole={navigation.switchRole}
            allowRoleSwitch={isPreviewSession}
          />
        ) : null}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: tokens.colors.background, flex: 1 },
  container: { flex: 1 },
  content: { flex: 1 },
});
