import { redirect } from 'next/navigation';

export default function AdminEditorRedirect() {
  redirect('/dashboard/editor');
}
