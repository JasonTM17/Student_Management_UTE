/**
 * Reorder-surface copy for the announcement feed editor.
 *
 * This text lives here rather than in `messages.ts` because `messages.ts` is a
 * shared dictionary that other work streams edit constantly, and the copy below
 * is only ever read by the admin announcements reorder flow and the shared
 * sortable component. Both locales are annotated with the same `ReorderCopy`
 * interface, so a missing or mis-shaped key in either language is a compile
 * error (a `Widen`-style derivation would collapse function-valued copy such as
 * `live.moved` to `{}` and lose its call signature).
 */

export interface ReorderCopy {
  trigger: {
    label: string;
    tooltip: string;
  };
  guard: {
    heading: string;
    /** A filter narrows the list to a subset, so its indexes are not global. */
    filtered: string;
    /** Any page but the first hides rows whose order would be left behind. */
    paged: string;
  };
  dialog: {
    intro: string;
    readOnly: string;
    save: string;
    saving: string;
  };
  handle: {
    label: string;
  };
  live: {
    /** Sentence for the `aria-live` region after a move, 1-based positions. */
    moved: (title: string, from: number, to: number, total: number) => string;
  };
  result: {
    success: string;
    /** Names how many writes failed out of how many were attempted. */
    partialFailure: (failed: number, attempted: number) => string;
    /** The pin order reached only this web instance, not the central store. */
    notDurable: string;
    error: string;
  };
}

export const reorderCopyEn: ReorderCopy = {
  trigger: {
    label: 'Reorder feed',
    tooltip: 'Drag to set the display order of the announcement feed',
  },
  guard: {
    heading: 'Reordering is paused so the saved feed order stays intact',
    filtered:
      'A filter is active, so this list is only part of the feed. Set Status to All and clear the semester and priority filters, then reorder.',
    paged:
      'You are viewing page 2 or later. Go back to page 1 before reordering, otherwise only this page would be rewritten.',
  },
  dialog: {
    intro:
      'Hold the ⠿ handle and drag to set the display order of the announcements on the feed. The item in position 01 is shown first.',
    readOnly:
      'This list is read-only right now. Apply the fix named above and reopen the dialog to drag items.',
    save: 'Save feed order',
    saving: 'Saving…',
  },
  handle: {
    label: 'Drag, or press the arrow keys, to reorder this announcement',
  },
  live: {
    moved: (title, from, to, total) =>
      `Moved "${title}" from position ${from} of ${total} to position ${to}.`,
  },
  result: {
    success: 'Announcement display order updated.',
    partialFailure: (failed, attempted) =>
      `${failed} of ${attempted} announcements could not be saved, so the feed order is only partly applied. The list has been reloaded from the server.`,
    error: 'The announcement order could not be saved. The list has been reloaded from the server.',
    notDurable:
      'The feed order was saved, but the homepage pin list could only be stored on this web server and will be lost on the next deploy.',
  },
};

export const reorderCopyVi: ReorderCopy = {
  trigger: {
    label: 'Sắp xếp thứ tự ghim',
    tooltip: 'Kéo thả để đặt thứ tự hiển thị của bảng tin',
  },
  guard: {
    heading: 'Tạm khoá sắp xếp để không ghi đè thứ tự đã lưu của toàn bảng tin',
    filtered:
      'Bộ lọc đang bật nên danh sách này chỉ là một phần của bảng tin. Hãy đặt Trạng thái sang Tất cả và bỏ lọc học kỳ cùng mức ưu tiên rồi mới sắp xếp.',
    paged:
      'Bạn đang từ trang 2 trở đi. Hãy về trang 1 trước khi sắp xếp, nếu không chỉ riêng trang này bị ghi lại thứ tự.',
  },
  dialog: {
    intro:
      'Giữ biểu tượng tay cầm ⠿ và kéo thả để đặt thứ tự ưu tiên của các thông báo trên Bảng tin. Thông báo ở vị trí 01 được hiển thị trước.',
    readOnly: 'Danh sách đang chỉ để xem. Hãy xử lý như hướng dẫn ở trên rồi mở lại hộp thoại để kéo thả.',
    save: 'Lưu thứ tự hiển thị',
    saving: 'Đang lưu…',
  },
  handle: {
    label: 'Kéo hoặc dùng phím mũi tên để sắp xếp thông báo này',
  },
  live: {
    moved: (title, from, to, total) =>
      `Đã chuyển “${title}” từ vị trí ${from} trong ${total} sang vị trí ${to}.`,
  },
  result: {
    success: 'Đã cập nhật thứ tự hiển thị thông báo thành công!',
    partialFailure: (failed, attempted) =>
      `${failed} / ${attempted} thông báo không lưu được nên thứ tự bảng tin mới chỉ áp dụng một phần. Danh sách đã được tải lại từ máy chủ.`,
    error: 'Không thể lưu thứ tự thông báo. Danh sách đã được tải lại từ máy chủ.',
    notDurable:
      'Thứ tự bảng tin đã lưu, nhưng danh sách ghim trang chủ chỉ ghi được trên web server này và sẽ mất ở lần deploy kế tiếp.',
  },
};
