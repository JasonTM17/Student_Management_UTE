import { useState } from 'react';
import { SafeAreaView, StatusBar, StyleSheet, View } from 'react-native';

import { apiClient } from '../api/client';
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
  const isPreviewSession = sessionKind === 'preview';
  const isAuthenticated = sessionKind === 'authenticated';
  const hasActiveSession = isPreviewSession || isAuthenticated;

  const navigation: MobileNavigation = {
    navigate(nextRoute) {
      if (!hasActiveSession) {
        return;
      }
      if (!canAccessScreen(role, nextRoute)) {
        setRoute(roleHome[role]);
        setMenuOpen(false);
        return;
      }
      setRoute(nextRoute);
      setMenuOpen(false);
    },
    enterPreview() {
      if (route !== 'auth.signIn' || apiClient.mode !== 'preview') {
        return;
      }
      setSessionKind('preview');
      setRoute(roleHome[role]);
      setMenuOpen(false);
    },
    goBack() {
      setRoute(hasActiveSession ? roleHome[role] : 'auth.signIn');
      setMenuOpen(false);
    },
    signOut() {
      apiClient.clearAccessToken();
      setSessionKind('signedOut');
      setRole('student');
      setRoute('auth.signIn');
      setMenuOpen(false);
    },
    switchRole(nextRole) {
      if (!isPreviewSession) {
        return;
      }
      setRole(nextRole);
      setRoute(roleHome[nextRole]);
      setMenuOpen(false);
    },
  };

  const ScreenComponent = screenComponents[route];

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={tokens.colors.background} />
      <View style={styles.container}>
        <ScreenComponent navigation={navigation} role={role} />
        {hasActiveSession ? (
          <BottomNavigation
            activeRoute={route}
            role={role}
            onNavigate={navigation.navigate}
            onMenu={() => setMenuOpen(true)}
          />
        ) : null}
        {menuOpen ? (
          <MenuPanel
            activeRoute={route}
            role={role}
            onClose={() => setMenuOpen(false)}
            onNavigate={navigation.navigate}
            onSwitchRole={navigation.switchRole}
          />
        ) : null}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: tokens.colors.background, flex: 1 },
  container: { flex: 1 },
});
