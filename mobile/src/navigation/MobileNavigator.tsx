import { useState } from 'react';
import { SafeAreaView, StatusBar, StyleSheet, View } from 'react-native';

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
  const isPreviewSession = sessionKind === 'preview';
  const isAuthenticated = sessionKind === 'authenticated';
  const hasActiveSession = isPreviewSession || isAuthenticated;

  const navigation: MobileNavigation = {
    navigate(nextRoute, options) {
      if (!hasActiveSession) {
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
