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

export function MobileNavigator() {
  const [route, setRoute] = useState<ScreenName>('auth.signIn');
  const [role, setRole] = useState<UserRole>('student');
  const [menuOpen, setMenuOpen] = useState(false);
  const isSignedIn = route !== 'auth.signIn';

  const navigation: MobileNavigation = {
    navigate(nextRoute) {
      if (!canAccessScreen(role, nextRoute)) {
        setRoute(roleHome[role]);
        setMenuOpen(false);
        return;
      }
      setRoute(nextRoute);
      setMenuOpen(false);
    },
    goBack() {
      setRoute(roleHome[role]);
      setMenuOpen(false);
    },
    signOut() {
      apiClient.clearAccessToken();
      setRole('student');
      setRoute('auth.signIn');
      setMenuOpen(false);
    },
    switchRole(nextRole) {
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
        {isSignedIn ? (
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
