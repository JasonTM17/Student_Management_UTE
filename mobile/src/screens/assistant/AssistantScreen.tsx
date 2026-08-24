import { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import {
  ApiClientError,
  apiClient,
  campusApi,
  createAssistantClientRequestId,
  type AssistantCitation,
  type AssistantConversation,
  type AssistantHistoryMessage,
  type AssistantLocale,
} from '../../api/client';
import {
  Badge,
  Button,
  Card,
  Field,
  ScreenShell,
  ScreenSpacer,
  StatePanel,
  UiText,
} from '../../components/Ui';
import { tokens } from '../../design/tokens';
import type { MobileScreenProps } from '../../navigation/types';

const NON_REPLAYABLE_TERMINAL_CODES = new Set([
  'TURN_CANCELLED',
  'TURN_PURGED',
  'FAILED_AMBIGUOUS',
  'TURN_TERMINAL_RACE',
  'TURN_NOT_ACTIVE',
]);

type ChatMessage = {
  id: string;
  from: 'assistant' | 'student';
  text: string;
  citations?: AssistantCitation[];
  model?: string | null;
  degraded?: boolean;
  reasonCode?: string | null;
  feedback?: 'UP' | 'DOWN' | null;
};

type Copy = {
  title: string;
  eyebrow: string;
  subtitle: string;
  trustTitle: string;
  preview: string;
  live: string;
  history: string;
  newChat: string;
  noHistory: string;
  noHistoryDescription: string;
  loadingHistory: string;
  historyError: string;
  retry: string;
  delete: string;
  confirmDelete: string;
  cancelDelete: string;
  ask: string;
  placeholder: string;
  send: string;
  stop: string;
  you: string;
  assistant: string;
  sources: string;
  fallback: string;
  model: string;
  emptyTitle: string;
  emptyDescription: string;
  errorTitle: string;
  errorDescription: string;
  quotaTitle: string;
  quotaDescription: string;
  sessionExpired: string;
  forbidden: string;
  offline: string;
  unavailable: string;
  progress: string;
  characterCount: (count: number) => string;
  helpful: string;
  notHelpful: string;
};

const copy: Record<AssistantLocale, Copy> = {
  vi: {
    title: 'Trợ lý học tập', eyebrow: 'Hỗ trợ có dẫn nguồn',
    subtitle: 'Đặt câu hỏi về luận văn và nhận câu trả lời bám sát tài liệu của trường.',
    trustTitle: 'RAG chuyên biệt cho luận văn',
    preview: 'Bản xem trước chỉ ghi nhận câu hỏi cục bộ. Chuyển sang live để gọi API Java sau khi đăng nhập.',
    live: 'Câu trả lời dùng ngữ cảnh luận văn được chọn ở máy chủ; không gửi lịch sử hay thông tin hồ sơ lên nhà cung cấp.',
    history: 'Lịch sử trò chuyện', newChat: 'Cuộc trò chuyện mới', noHistory: 'Chưa có lịch sử',
    noHistoryDescription: 'Các cuộc trò chuyện của bạn sẽ xuất hiện ở đây và có thể xoá riêng từng cuộc.',
    loadingHistory: 'Đang tải lịch sử…', historyError: 'Không thể tải lịch sử trò chuyện.', retry: 'Thử lại', delete: 'Xoá', confirmDelete: 'Xác nhận xoá', cancelDelete: 'Huỷ',
    ask: 'Câu hỏi của bạn', placeholder: 'Ví dụ: Mốc tiến độ luận văn tiếp theo là khi nào?', send: 'Gửi câu hỏi', stop: 'Dừng trả lời',
    you: 'BẠN', assistant: 'TRỢ LÝ', sources: 'Nguồn tham khảo', fallback: 'Đang dùng câu trả lời dự phòng', model: 'Mô hình',
    emptyTitle: 'Sẵn sàng hỗ trợ', emptyDescription: 'Bắt đầu bằng một câu hỏi cụ thể về đề tài, tiến độ hoặc quy trình luận văn.',
    errorTitle: 'Trợ lý chưa trả lời được', errorDescription: 'Kiểm tra kết nối rồi thử lại. Câu hỏi của bạn vẫn được giữ để gửi lại.', sessionExpired: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', forbidden: 'Tài khoản hiện không được phép dùng trợ lý.', offline: 'Không có kết nối mạng. Hãy kiểm tra mạng rồi thử lại.',
    quotaTitle: 'Bạn đã đạt giới hạn hôm nay', quotaDescription: 'Hạn mức RAG theo ngày đã được sử dụng. Hãy thử lại vào ngày mai.',
    unavailable: 'Kho tri thức hiện chưa sẵn sàng.', progress: 'Mở tiến độ luận văn', characterCount: (count) => `${count}/2000 ký tự`, helpful: 'Hữu ích', notHelpful: 'Chưa đúng',
  },
  en: {
    title: 'Academic assistant', eyebrow: 'Grounded support',
    subtitle: 'Ask about your thesis and receive answers grounded in the university knowledge base.',
    trustTitle: 'Thesis-focused RAG',
    preview: 'Preview records questions locally. Switch to live mode to call the Java API after sign-in.',
    live: 'Answers use server-selected thesis context; conversation history and profile data stay out of provider requests.',
    history: 'Conversation history', newChat: 'New conversation', noHistory: 'No history yet',
    noHistoryDescription: 'Your conversations will appear here and can be deleted one at a time.',
    loadingHistory: 'Loading history…', historyError: 'Conversation history could not be loaded.', retry: 'Retry', delete: 'Delete', confirmDelete: 'Confirm delete', cancelDelete: 'Cancel',
    ask: 'Your question', placeholder: 'For example: When is my next thesis milestone?', send: 'Send question', stop: 'Stop answer',
    you: 'YOU', assistant: 'ASSISTANT', sources: 'Sources', fallback: 'Using a fallback answer', model: 'Model',
    emptyTitle: 'Ready when you are', emptyDescription: 'Start with a focused question about your topic, progress, or thesis process.',
    errorTitle: 'The assistant could not answer', errorDescription: 'Check your connection and try again. Your question is kept for retry.', sessionExpired: 'Your session expired. Please sign in again.', forbidden: 'This account is not allowed to use the assistant.', offline: 'No network connection. Check your connection and retry.',
    quotaTitle: 'Daily limit reached', quotaDescription: 'Your daily RAG allowance has been used. Please try again tomorrow.',
    unavailable: 'The knowledge base is not available right now.', progress: 'Open thesis progress', characterCount: (count) => `${count}/2000 characters`, helpful: 'Helpful', notHelpful: 'Not helpful',
  },
};

function safeText(value: string | null | undefined, maxLength = 12_000) {
  return (value ?? '').replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '').slice(0, maxLength).trim();
}

function toChatMessage(message: AssistantHistoryMessage): ChatMessage {
  return { id: message.id, from: message.role.toLowerCase() === 'user' ? 'student' : 'assistant', text: safeText(message.content), citations: message.citations, model: message.model, degraded: message.degraded, reasonCode: message.reasonCode, feedback: message.feedback ?? null };
}

function historyTitle(conversation: AssistantConversation, locale: AssistantLocale) {
  const title = safeText(conversation.title, 80);
  return title || (locale === 'vi' ? 'Cuộc trò chuyện luận văn' : 'Thesis conversation');
}

export function AssistantChatScreen({ navigation }: MobileScreenProps) {
  const [locale, setLocale] = useState<AssistantLocale>('vi');
  const [message, setMessage] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [conversations, setConversations] = useState<AssistantConversation[]>([]);
  const [conversationId, setConversationId] = useState<string | undefined>();
  const [isSending, setIsSending] = useState(false);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [requestError, setRequestError] = useState<{ code?: string; message: string } | null>(null);
  const [lastFailedPrompt, setLastFailedPrompt] = useState<string | null>(null);
  const [lastFailedClientRequestId, setLastFailedClientRequestId] = useState<string | null>(null);
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);
  const [deletingConversationId, setDeletingConversationId] = useState<string | null>(null);
  const [feedbackBusyId, setFeedbackBusyId] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const activeClientRequestIdRef = useRef<string | null>(null);
  const activePromptRef = useRef<string | null>(null);
  // Keep the exact conversation value used by the request key. A retry may
  // happen after the visible selection has changed, but it must replay the
  // original canonical payload rather than creating an idempotency conflict.
  const activeConversationIdRef = useRef<string | undefined>();
  const lastFailedConversationIdRef = useRef<string | undefined>();
  const isPreview = apiClient.mode === 'preview';
  const t = copy[locale];

  const loadConversations = useCallback(async () => {
    if (isPreview) return;
    setIsLoadingHistory(true); setHistoryError(null);
    try { setConversations((await campusApi.assistantConversationsPage(20)).data); }
    catch (error) { setHistoryError(error instanceof Error ? error.message : t.historyError); }
    finally { setIsLoadingHistory(false); }
  }, [isPreview, t.historyError]);

  useEffect(() => {
    void loadConversations();
  }, [loadConversations]);

  useEffect(() => {
    return () => {
      const clientRequestId = activeClientRequestIdRef.current;
      if (clientRequestId && !isPreview) {
        void campusApi.cancelAssistantRequest(clientRequestId).catch(() => undefined);
      }
      abortRef.current?.abort();
    };
  }, [isPreview]);

  const selectConversation = async (selectedId: string) => {
    if (isSending || selectedId === conversationId) return;
    setIsLoadingHistory(true); setHistoryError(null);
    try {
      const history = await campusApi.assistantConversationMessagesPage(selectedId, 50);
      setConversationId(selectedId); setMessages(history.data.map(toChatMessage)); setRequestError(null); setLastFailedPrompt(null); setLastFailedClientRequestId(null); lastFailedConversationIdRef.current = undefined;
    } catch (error) { setHistoryError(error instanceof Error ? error.message : t.historyError); }
    finally { setIsLoadingHistory(false); }
  };

  const deleteConversation = async (selectedId: string) => {
    if (isSending || deletingConversationId) return;
    if (pendingDeleteId !== selectedId) { setPendingDeleteId(selectedId); return; }
    setDeletingConversationId(selectedId);
    try {
      await campusApi.deleteAssistantConversation(selectedId);
      setConversations((current) => current.filter((item) => item.id !== selectedId));
      if (selectedId === conversationId) { setConversationId(undefined); setMessages([]); setRequestError(null); lastFailedConversationIdRef.current = undefined; }
      setPendingDeleteId(null);
    } catch (error) { setHistoryError(error instanceof Error ? error.message : t.historyError); }
    finally { setDeletingConversationId(null); }
  };

  const sendMessage = async (promptOverride?: string, clientRequestIdOverride?: string) => {
    const trimmedMessage = safeText(promptOverride ?? message, 2000);
    if (!trimmedMessage || isSending) return;
    const canReuseKey = Boolean(clientRequestIdOverride)
      && !NON_REPLAYABLE_TERMINAL_CODES.has(requestError?.code ?? '');
    const clientRequestId = canReuseKey ? clientRequestIdOverride as string : createAssistantClientRequestId();
    const requestedConversationId = clientRequestIdOverride
      ? lastFailedConversationIdRef.current
      : conversationId;
    setRequestError(null); setLastFailedPrompt(null); setLastFailedClientRequestId(null);
    if (!clientRequestIdOverride) {
      setMessages((current) => [...current, { id: `local-user-${clientRequestId}`, from: 'student', text: trimmedMessage }]);
    }
    setMessage('');
    if (isPreview) {
      setMessages((current) => [...current, { id: `local-preview-${Date.now()}`, from: 'assistant', text: locale === 'vi' ? 'Đã ghi nhận trong bản xem trước. Live mode sẽ trả lời qua API Java sau khi đăng nhập.' : 'Preview noted. Live mode will answer through the Java assistant API after sign-in.', model: 'preview-local', reasonCode: 'PREVIEW' }]);
      return;
    }
    const controller = new AbortController(); abortRef.current = controller; activeClientRequestIdRef.current = clientRequestId; activePromptRef.current = trimmedMessage; activeConversationIdRef.current = requestedConversationId; setIsSending(true);
    try {
      const reply = await campusApi.assistantChat(trimmedMessage, locale, requestedConversationId, clientRequestId, { signal: controller.signal });
      if (reply.conversationId) setConversationId(reply.conversationId);
      setMessages((current) => [...current, { id: reply.messageId ?? `local-assistant-${Date.now()}`, from: 'assistant', text: safeText(reply.answer) || t.unavailable, citations: reply.citations, model: safeText(reply.model, 80), degraded: reply.degraded, reasonCode: reply.reasonCode }]);
      lastFailedConversationIdRef.current = undefined;
      void loadConversations();
    } catch (assistantError) {
      if (controller.signal.aborted) return;
      const apiError = assistantError instanceof ApiClientError ? assistantError : undefined;
      const code = apiError?.code ?? (apiError?.status === 401 ? 'SESSION_EXPIRED' : apiError?.status === 403 ? 'FORBIDDEN' : undefined);
      const message = apiError?.status === 401 ? t.sessionExpired : apiError?.status === 403 ? t.forbidden : apiError?.status === 0 || assistantError instanceof TypeError ? t.offline : apiError?.message ?? t.errorDescription;
      setRequestError({ code, message }); setLastFailedPrompt(trimmedMessage); setLastFailedClientRequestId(clientRequestId); lastFailedConversationIdRef.current = requestedConversationId;
    } finally { if (abortRef.current === controller) abortRef.current = null; activeClientRequestIdRef.current = null; activePromptRef.current = null; activeConversationIdRef.current = undefined; setIsSending(false); }
  };

  const stopSending = async () => {
    const clientRequestId = activeClientRequestIdRef.current;
    if (!clientRequestId) return;
    const prompt = activePromptRef.current;
    let cancelAccepted = false;
    let replayed = false;
    try { await campusApi.cancelAssistantRequest(clientRequestId); cancelAccepted = true; }
    catch (error) {
      if (error instanceof ApiClientError && error.status === 409 && activePromptRef.current) {
        // Completion won the server CAS. Replay the committed result with the
        // same key before aborting the local reader, so Stop cannot erase it.
        try {
          const reply = await campusApi.assistantChat(activePromptRef.current, locale, activeConversationIdRef.current, clientRequestId);
          if (reply.conversationId) setConversationId(reply.conversationId);
          setMessages((current) => [...current, { id: reply.messageId ?? `local-assistant-${Date.now()}`, from: 'assistant', text: safeText(reply.answer) || t.unavailable, citations: reply.citations, model: safeText(reply.model, 80), degraded: reply.degraded, reasonCode: reply.reasonCode }]);
          void loadConversations();
          replayed = true;
        } catch (replayError) {
          setRequestError({ code: replayError instanceof ApiClientError ? replayError.code : undefined, message: replayError instanceof ApiClientError ? replayError.message : t.errorDescription });
        }
      } else {
        setRequestError({ code: error instanceof ApiClientError ? error.code : undefined, message: error instanceof ApiClientError ? error.message : t.errorDescription });
      }
    }
    // The local transport boundary remains `finally { abortRef.current?.abort() }`
    // after the owner-scoped server CAS (or replay) has completed.
    finally {
      if (prompt && cancelAccepted && !replayed) {
        setMessages((current) => [...current, { id: `local-cancelled-${Date.now()}`, from: 'assistant', text: locale === 'vi' ? 'Đã dừng câu trả lời. Bạn có thể thử lại.' : 'Generation stopped. You can retry when ready.', degraded: true, reasonCode: 'CANCELLED' }]);
        setLastFailedPrompt(prompt);
        setLastFailedClientRequestId(null);
      }
      abortRef.current?.abort(); abortRef.current = null; activeClientRequestIdRef.current = null; activePromptRef.current = null; activeConversationIdRef.current = undefined; setIsSending(false);
    }
  };
  const submitFeedback = async (messageId: string, rating: 'UP' | 'DOWN') => {
    if (messageId.startsWith('local-') || feedbackBusyId) return;
    setFeedbackBusyId(messageId);
    try {
      await campusApi.putAssistantFeedback(messageId, rating);
      setMessages((current) => current.map((item) => item.id === messageId ? { ...item, feedback: rating } : item));
    } catch (error) {
      setRequestError({ code: error instanceof ApiClientError ? error.code : undefined, message: error instanceof ApiClientError ? error.message : t.errorDescription });
    } finally { setFeedbackBusyId(null); }
  };
  const resetConversation = () => { if (isSending) return; setConversationId(undefined); setMessages([]); setRequestError(null); setLastFailedPrompt(null); setLastFailedClientRequestId(null); setPendingDeleteId(null); };
  const requestErrorIsQuota = requestError?.code === 'QUOTA_EXCEEDED' || requestError?.code === 'DAILY_QUOTA_EXCEEDED';

  return (
    <ScreenShell title={t.title} eyebrow={t.eyebrow} subtitle={t.subtitle}>
      <View style={styles.localeRow} accessibilityRole="tablist">
        <UiText variant="label" tone="muted">Language / Ngôn ngữ</UiText>
        <View style={styles.localeActions}>{(['vi', 'en'] as const).map((option) => <Button key={option} label={option.toUpperCase()} onPress={() => setLocale(option)} variant={locale === option ? 'primary' : 'secondary'} accessibilityLabel={option === 'vi' ? 'Use Vietnamese' : 'Use English'} style={styles.localeButton} />)}</View>
      </View>
      <Card tone="low" style={styles.trustCard}>
        <View style={styles.trustHeader}><UiText variant="label" tone="primary">{t.trustTitle}</UiText><Badge label={isPreview ? 'PREVIEW' : 'LIVE'} tone={isPreview ? 'warning' : 'success'} /></View>
        <UiText variant="bodySmall" tone="muted">{isPreview ? t.preview : t.live}</UiText>
      </Card>
      {!isPreview ? <><ScreenSpacer /><Card style={styles.historyCard}>
        <View style={styles.sectionHeader}><UiText accessibilityRole="header" variant="headlineSmall">{t.history}</UiText><Button label={t.newChat} onPress={resetConversation} variant="text" /></View>
        {isLoadingHistory ? <StatePanel kind="loading" title={t.loadingHistory} /> : null}
        {historyError ? <StatePanel kind="error" title={t.historyError} description={historyError} actionLabel={t.retry} onAction={() => void loadConversations()} /> : null}
        {!isLoadingHistory && !historyError && conversations.length === 0 ? <StatePanel kind="empty" title={t.noHistory} description={t.noHistoryDescription} /> : null}
        {conversations.map((conversation) => <View key={conversation.id} style={styles.historyRow}><Button label={historyTitle(conversation, locale)} onPress={() => void selectConversation(conversation.id)} variant={conversation.id === conversationId ? 'primary' : 'secondary'} style={styles.historySelect} />{pendingDeleteId === conversation.id ? <><Button label={t.confirmDelete} loading={deletingConversationId === conversation.id} onPress={() => void deleteConversation(conversation.id)} variant="text" accessibilityLabel={`${t.confirmDelete} ${historyTitle(conversation, locale)}`} style={styles.deleteButton} /><Button label={t.cancelDelete} disabled={deletingConversationId === conversation.id} onPress={() => setPendingDeleteId(null)} variant="text" style={styles.deleteButton} /></> : <Button label={t.delete} onPress={() => void deleteConversation(conversation.id)} variant="text" accessibilityLabel={`${t.delete} ${historyTitle(conversation, locale)}`} style={styles.deleteButton} />}</View>)}
      </Card></> : null}
      <ScreenSpacer />
      {messages.length === 0 && !isSending && !requestError ? <StatePanel kind="empty" title={t.emptyTitle} description={t.emptyDescription} /> : null}
      {messages.map((chatMessage) => <View key={chatMessage.id} style={[styles.messageRow, chatMessage.from === 'student' ? styles.studentRow : undefined]}><Card tone={chatMessage.from === 'student' ? 'primary' : 'card'} style={styles.messageCard}>
        <View style={styles.messageHeader}><UiText variant="meta" tone={chatMessage.from === 'student' ? 'primary' : 'muted'}>{chatMessage.from === 'student' ? t.you : t.assistant}</UiText>{chatMessage.from === 'assistant' && chatMessage.degraded ? <Badge label={t.fallback} tone="warning" /> : null}</View>
        <UiText variant="bodySmall" tone={chatMessage.from === 'student' ? 'default' : 'muted'} style={styles.messageText}>{safeText(chatMessage.text) || t.unavailable}</UiText>
        {chatMessage.from === 'assistant' && chatMessage.model ? <UiText variant="meta" tone="muted" style={styles.reasonCode}>{t.model}: {chatMessage.model}</UiText> : null}
        {chatMessage.from === 'assistant' && chatMessage.reasonCode && chatMessage.reasonCode !== 'ANSWERED' ? <UiText variant="meta" tone={chatMessage.degraded ? 'warning' : 'muted'} style={styles.reasonCode}>{chatMessage.reasonCode === 'KNOWLEDGE_UNAVAILABLE' ? t.unavailable : chatMessage.reasonCode}</UiText> : null}
        {chatMessage.citations?.length ? <View style={styles.sources}><UiText variant="label" tone="primary">{t.sources}</UiText>{chatMessage.citations.map((citation) => <View key={citation.id} style={styles.citation}><UiText variant="label" tone="primary">{safeText(citation.title, 160)}</UiText><UiText variant="meta" tone="muted">{safeText(citation.source, 120)} · {citation.locale.toUpperCase()}</UiText><UiText variant="bodySmall" tone="muted" style={styles.citationExcerpt}>{safeText(citation.excerpt, 600)}</UiText></View>)}</View> : null}
        {chatMessage.from === 'assistant' && !chatMessage.id.startsWith('local-') ? <View style={styles.feedbackRow}><Button label={chatMessage.feedback === 'UP' ? `✓ ${t.helpful}` : t.helpful} loading={feedbackBusyId === chatMessage.id} onPress={() => void submitFeedback(chatMessage.id, 'UP')} variant="text" /><Button label={chatMessage.feedback === 'DOWN' ? `✓ ${t.notHelpful}` : t.notHelpful} loading={feedbackBusyId === chatMessage.id} onPress={() => void submitFeedback(chatMessage.id, 'DOWN')} variant="text" /></View> : null}
      </Card></View>)}
      {isSending ? <StatePanel kind="loading" title={locale === 'vi' ? 'Đang tìm câu trả lời…' : 'Finding an answer…'} /> : null}
      {requestError ? <StatePanel kind="error" title={requestErrorIsQuota ? t.quotaTitle : requestError.code === 'SESSION_EXPIRED' ? t.sessionExpired : requestError.code === 'FORBIDDEN' ? t.forbidden : t.errorTitle} description={requestErrorIsQuota ? t.quotaDescription : requestError.message} actionLabel={lastFailedPrompt ? t.retry : undefined} onAction={lastFailedPrompt ? () => void sendMessage(lastFailedPrompt, lastFailedClientRequestId ?? undefined) : undefined} /> : null}
      <ScreenSpacer />
      <Card style={styles.composer}><Field label={t.ask} multiline editable={!isSending} maxLength={2000} onChangeText={setMessage} placeholder={t.placeholder} value={message} helperText={t.characterCount(message.length)} accessibilityHint={t.emptyDescription} /><View style={styles.composerActions}><Button label={t.send} loading={isSending} disabled={!message.trim()} onPress={() => void sendMessage()} style={styles.sendButton} />{isSending ? <Button label={t.stop} onPress={stopSending} variant="secondary" style={styles.stopButton} /> : null}</View></Card>
      <Button label={t.progress} onPress={() => navigation.navigate('thesis.progress')} variant="secondary" style={styles.progressButton} />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  localeRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginBottom: tokens.spacing.md },
  localeActions: { flexDirection: 'row', gap: tokens.spacing.xs },
  localeButton: { minWidth: tokens.layout.touchTarget, paddingHorizontal: tokens.spacing.sm },
  trustCard: { padding: tokens.spacing.md },
  trustHeader: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginBottom: tokens.spacing.xs },
  historyCard: { padding: tokens.spacing.md },
  sectionHeader: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginBottom: tokens.spacing.sm },
  historyRow: { alignItems: 'center', flexDirection: 'row', gap: tokens.spacing.xs, marginTop: tokens.spacing.xs },
  historySelect: { flex: 1, minWidth: 0 },
  deleteButton: { minWidth: tokens.layout.touchTarget },
  messageRow: { alignItems: 'flex-start', marginBottom: tokens.spacing.sm },
  studentRow: { alignItems: 'flex-end' },
  messageCard: { maxWidth: '92%', padding: tokens.spacing.md },
  messageHeader: { alignItems: 'center', flexDirection: 'row', gap: tokens.spacing.sm, justifyContent: 'space-between' },
  messageText: { marginTop: tokens.spacing.xs },
  reasonCode: { marginTop: tokens.spacing.sm },
  sources: { marginTop: tokens.spacing.md },
  citation: { borderLeftColor: tokens.colors.primary, borderLeftWidth: 2, marginTop: tokens.spacing.sm, paddingLeft: tokens.spacing.sm },
  citationExcerpt: { marginTop: tokens.spacing.xs },
  feedbackRow: { flexDirection: 'row', gap: tokens.spacing.xs, marginTop: tokens.spacing.sm },
  composer: { padding: tokens.spacing.md },
  composerActions: { flexDirection: 'row', gap: tokens.spacing.sm },
  sendButton: { flex: 1 },
  stopButton: { flex: 1 },
  progressButton: { marginTop: tokens.spacing.sm },
});
