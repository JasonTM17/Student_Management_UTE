import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Button, Card, Field, ScreenShell, ScreenSpacer, UiText } from '../../components/Ui';
import { tokens } from '../../design/tokens';
import type { MobileScreenProps } from '../../navigation/types';

type ChatMessage = { from: 'assistant' | 'student'; text: string };

const starterMessages: ChatMessage[] = [
  { from: 'assistant', text: 'Hello Minh. I can help you find a course, understand a grade, or plan your next thesis milestone.' },
  { from: 'student', text: 'What should I prepare before thesis registration?' },
  { from: 'assistant', text: 'Confirm your group members, shortlist one topic, and review the supervisor availability before submitting.' },
];

export function AssistantChatScreen({ navigation }: MobileScreenProps) {
  const [message, setMessage] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>(starterMessages);

  const sendMessage = () => {
    const trimmedMessage = message.trim();
    if (!trimmedMessage) {
      return;
    }

    setMessages((current) => [
      ...current,
      { from: 'student', text: trimmedMessage },
      { from: 'assistant', text: 'I have noted that. The live assistant contract will answer from your authenticated academic context.' },
    ]);
    setMessage('');
  };

  return (
    <ScreenShell title="Academic assistant" eyebrow="Guided support" subtitle="A focused place for your next academic question.">
      <Card tone="low" style={styles.trustCard}>
        <UiText variant="label" tone="primary">Context-aware by design</UiText>
        <UiText variant="bodySmall" tone="muted">The mobile seam is ready for the Java assistant route; this preview keeps responses local.</UiText>
      </Card>
      <ScreenSpacer />
      {messages.map((chatMessage, index) => (
        <View key={`${chatMessage.from}-${index}`} style={[styles.messageRow, chatMessage.from === 'student' ? styles.studentRow : undefined]}>
          <Card tone={chatMessage.from === 'student' ? 'primary' : 'card'} style={styles.messageCard}>
            <UiText variant="meta" tone={chatMessage.from === 'student' ? 'primary' : 'muted'}>
              {chatMessage.from === 'student' ? 'YOU' : 'ASSISTANT'}
            </UiText>
            <UiText variant="bodySmall" tone={chatMessage.from === 'student' ? 'default' : 'muted'} style={styles.messageText}>
              {chatMessage.text}
            </UiText>
          </Card>
        </View>
      ))}
      <ScreenSpacer />
      <Card style={styles.composer}>
        <Field
          label="Ask a question"
          multiline
          onChangeText={setMessage}
          placeholder="e.g. When is my next thesis milestone?"
          value={message}
        />
        <Button label="Send message" onPress={sendMessage} />
      </Card>
      <Button label="Open thesis progress" onPress={() => navigation.navigate('thesis.progress')} variant="secondary" style={styles.progressButton} />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  trustCard: { padding: tokens.spacing.md },
  messageRow: { alignItems: 'flex-start', marginBottom: tokens.spacing.sm },
  studentRow: { alignItems: 'flex-end' },
  messageCard: { maxWidth: '88%', padding: tokens.spacing.md },
  messageText: { marginTop: tokens.spacing.xs },
  composer: { padding: tokens.spacing.md },
  progressButton: { marginTop: tokens.spacing.sm },
});

