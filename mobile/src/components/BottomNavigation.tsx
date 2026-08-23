import { Pressable, StyleSheet, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { tokens } from '../design/tokens';
import {
  getBottomNavigation,
  type ScreenName,
  type UserRole,
} from '../navigation/routes';
import { UiText } from './Ui';

interface BottomNavigationProps {
  activeRoute: ScreenName;
  role: UserRole;
  onNavigate(route: ScreenName): void;
  onMenu(): void;
}

export function BottomNavigation({ activeRoute, role, onNavigate, onMenu }: BottomNavigationProps) {
  const items = getBottomNavigation(role);

  return (
    <View style={styles.navigation}>
      <View style={styles.navigationInner}>
        {items.map((item) => {
          const active = item.route === activeRoute;

          return (
            <Pressable
              key={item.route}
              accessibilityLabel={item.label}
              accessibilityRole="button"
              accessibilityState={{ selected: active }}
              onPress={() => onNavigate(item.route)}
              style={({ pressed }) => [styles.item, pressed ? styles.pressed : undefined]}
            >
              <View style={[styles.icon, active ? styles.activeIcon : undefined]}>
                <MaterialCommunityIcons
                  color={active ? tokens.colors.primary : tokens.colors.textMuted}
                  name={item.icon}
                  size={20}
                />
              </View>
              <UiText variant="meta" tone={active ? 'primary' : 'muted'}>
                {item.label}
              </UiText>
            </Pressable>
          );
        })}
        <Pressable
          accessibilityLabel="Open menu"
          accessibilityRole="button"
          onPress={onMenu}
          style={({ pressed }) => [styles.item, pressed ? styles.pressed : undefined]}
        >
          <View style={styles.icon}>
            <MaterialCommunityIcons color={tokens.colors.textMuted} name="menu" size={20} />
          </View>
          <UiText variant="meta" tone="muted">
            Menu
          </UiText>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  navigation: {
    backgroundColor: tokens.colors.card,
    borderTopColor: tokens.colors.outlineVariant,
    borderTopWidth: 1,
    minHeight: tokens.layout.bottomNavigationHeight,
    paddingHorizontal: tokens.spacing.xs,
  },
  navigationInner: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-around', minHeight: tokens.layout.bottomNavigationHeight },
  item: { alignItems: 'center', justifyContent: 'center', minHeight: tokens.layout.touchTarget, minWidth: 60, paddingHorizontal: tokens.spacing.xs },
  icon: { alignItems: 'center', borderRadius: tokens.radii.control, height: 30, justifyContent: 'center', marginBottom: 2, width: 40 },
  activeIcon: { backgroundColor: tokens.colors.primaryFixed },
  pressed: { backgroundColor: tokens.colors.surfaceLow, borderRadius: tokens.radii.control },
});
