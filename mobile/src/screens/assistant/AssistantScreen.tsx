import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { ApiClientError, apiClient, campusApi, type AssistantCitation } from '../../api/client';
import { Button, Card, Field, ScreenShell, ScreenSpacer, UiText } from '../../components/Ui';
import { tokens } from '../../design/tokens';
import type { MobileScreenProps } from '../../navigation/types';

type ChatMessage = {
  from: 'assistant' | 'student';
  text: string;
  citations?: AssistantCitation[];
  degraded?: boolean;
  reasonCode?: string;
};

export function AssistantChatScreen({ navigation }: MobileScreenProps) {
  const [message, setMessage] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isSending, setIsSending] = useState(false);
  const isPreview = apiClient.mode === 'preview';

  const sendMessage = async () => {
    const trimmedMessage = message.trim();
    if (!trimmedMessage || isSending) {
      return;
    }

    setMessages((current) => [
      ...current,
      { from: 'student', text: trimmedMessage },
    ]);
    setMessage('');

    if (isPreview) {
      setMessages((current) => [
        ...current,
        { from: 'assistant', text: 'Preview noted. Live mode will answer through the Java assistant contract after authentication.' },
      ]);
      return;
    }

    setIsSending(true);
    try {
      const reply = await campusApi.assistantChat(trimmedMessage, 'vi');
      setMessages((current) => [
        ...current,
        {
          from: 'assistant',
          text: reply.answer,
          citations: reply.citations,
          degraded: reply.degraded,
          reasonCode: reply.reasonCode,
        },
      ]);
    } catch (assistantError) {
      setMessages((current) => [
        ...current,
        {
          from: 'assistant',
          text:
            assistantError instanceof ApiClientError
              ? `The Java assistant route could not answer yet: ${assistantError.message}`
              : 'The Java assistant route could not answer yet.',
        },
      ]);
    } finally {
      setIsSending(false);
    }
  };

  return (
    <ScreenShell title="Academic assistant" eyebrow="Guided support" subtitle="A focused place for your next academic question.">
      <Card tone="low" style={styles.trustCard}>
        <UiText variant="label" tone="primary">Context-aware by design</UiText>
        <UiText variant="bodySmall" tone="muted">
          {isPreview
            ? 'Preview keeps responses local. Live mode calls the Java assistant route with your bearer session.'
            : 'Live mode calls the Java assistant route with your bearer session and keeps the fallback label visible.'}
        </UiText>
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
            {chatMessage.from === 'assistant' && chatMessage.reasonCode ? (
              <UiText variant="meta" tone={chatMessage.degraded ? 'warning' : 'muted'} style={styles.reasonCode}>
                {chatMessage.degraded ? 'DEGRADED' : chatMessage.reasonCode}
              </UiText>
            ) : null}
            {chatMessage.citations?.map((citation) => (
              <View key={citation.id} style={styles.citation}>
                <UiText variant="label" tone="primary">{citation.title}</UiText>
                <UiText variant="meta" tone="muted">{citation.source} · {citation.locale.toUpperCase()}</UiText>
                <UiText variant="bodySmall" tone="muted" style={styles.citationExcerpt}>{citation.excerpt}</UiText>
              </View>
            ))}
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
        <Button label="Send message" loading={isSending} onPress={sendMessage} />
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
  reasonCode: { marginTop: tokens.spacing.sm },
  citation: { borderLeftColor: tokens.colors.primary, borderLeftWidth: 2, marginTop: tokens.spacing.sm, paddingLeft: tokens.spacing.sm },
  citationExcerpt: { marginTop: tokens.spacing.xs },
  composer: { padding: tokens.spacing.md },
  progressButton: { marginTop: tokens.spacing.sm },
});
