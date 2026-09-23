"""Disposable PostgreSQL proof for concurrent governed publish and archive.

Requires psycopg 3 and ASSISTANT_SUPABASE_TEST_DSN pointing to a new local
campuscore_supabase_e2e database. Never run against an existing project.
"""

import os
from pathlib import Path
import threading
import time

import psycopg
from psycopg.types.json import Jsonb


ROOT = Path(__file__).resolve().parents[2]
DSN = os.environ["ASSISTANT_SUPABASE_TEST_DSN"]


def connect():
    return psycopg.connect(DSN, autocommit=True)


def rpc(conn, action, document_id, actor, payload=None):
    return conn.execute(
        "select assistant.knowledge_admin(%s, %s, %s, %s)",
        (action, document_id, Jsonb(payload or {}), actor),
    ).fetchone()[0]


def payload(slug, content):
    return {
        "domain": "POLICY", "locale": "en", "slug": slug,
        "title": f"Title {slug}", "content": content,
        "source": "test faculty", "priority": 10, "visibility": "PUBLIC",
    }


def setup(conn):
    if conn.info.dbname != "campuscore_supabase_e2e" or conn.info.host not in (
        "127.0.0.1", "localhost"
    ):
        raise RuntimeError("Refusing non-disposable or non-local database")
    if conn.execute("select to_regnamespace('assistant')").fetchone()[0] is not None:
        raise RuntimeError("Refusing database with an existing assistant schema")
    conn.execute("create schema auth; create schema extensions")
    conn.execute("create extension pgcrypto with schema extensions")
    conn.execute(
        "create function auth.jwt() returns jsonb language sql stable "
        "as $$select '{}'::jsonb$$"
    )
    conn.execute("""
        do $$ begin
            if not exists (select 1 from pg_roles where rolname='anon') then create role anon; end if;
            if not exists (select 1 from pg_roles where rolname='authenticated') then create role authenticated; end if;
            if not exists (select 1 from pg_roles where rolname='service_role') then create role service_role; end if;
        end $$;
    """)
    for migration in (
        "20260823073842_assistant_knowledge_authoring.sql",
        "20260823160000_assistant_authoring_admin_only.sql",
        "20260901090000_assistant_release_authority.sql",
        "20260923180000_specialized_knowledge_domain.sql",
        "20260923181500_archive_knowledge_release.sql",
    ):
        conn.execute((ROOT / "supabase" / "migrations" / migration).read_text(encoding="utf-8"))
    access = conn.execute("""
        select has_function_privilege('service_role',
                   'assistant.knowledge_admin(text,uuid,jsonb,text)', 'EXECUTE'),
               has_function_privilege('service_role',
                   'assistant.knowledge_admin_unlocked(text,uuid,jsonb,text)', 'EXECUTE'),
               prosecdef from pg_proc
         where oid = 'assistant.knowledge_admin(text,uuid,jsonb,text)'::regprocedure
    """).fetchone()
    assert access == (True, False, True), f"RPC privilege boundary failed: {access}"


def assert_active_consistent(conn, archived_id, revised_id):
    row = conn.execute("""
        select id, corpus_hash, row_count from assistant.knowledge_release
        where status='PUBLISHED'
    """).fetchone()
    if row is None:
        raise AssertionError("No published release")
    release_id, declared_hash, declared_count = row
    actual_count, actual_hash = conn.execute("""
        select count(*)::integer,
               encode(extensions.digest(coalesce(string_agg(
                   concat_ws('|', source_id, coalesce(revision_id::text,''), version::text,
                             domain, slug, locale, title, content, source,
                             priority::text, active::text, visibility),
                   E'\\n' order by source_id), ''), 'sha256'), 'hex')
          from assistant.knowledge_release_document where release_id=%s
    """, (release_id,)).fetchone()
    if (declared_count, declared_hash) != (actual_count, actual_hash):
        raise AssertionError("Published manifest differs from its immutable rows")
    sources = {row[0] for row in conn.execute(
        "select source_id from assistant.knowledge_release_document where release_id=%s",
        (release_id,),
    )}
    if str(archived_id) in sources or str(revised_id) not in sources:
        raise AssertionError("Published corpus did not reflect both operations")


def main():
    with connect() as conn:
        setup(conn)
        first = rpc(conn, "CREATE", None, "author-a", payload("archive-target", "Original guidance A."))
        first_id = first["documentId"]
        rpc(conn, "SUBMIT", first_id, "author-a")
        rpc(conn, "PUBLISH", first_id, "reviewer-a")
        second = rpc(conn, "CREATE", None, "author-b", payload("publish-target", "Original guidance B."))
        second_id = second["documentId"]
        rpc(conn, "SUBMIT", second_id, "author-b")
        rpc(conn, "PUBLISH", second_id, "reviewer-b")
        rpc(conn, "UPDATE", second_id, "author-b", payload("publish-target", "Revised guidance B."))
        rpc(conn, "SUBMIT", second_id, "author-b")

    published = threading.Event()
    release_publish = threading.Event()
    archived = threading.Event()
    failures = []

    def publish():
        try:
            with psycopg.connect(DSN) as conn:
                rpc(conn, "PUBLISH", second_id, "reviewer-b")
                published.set()
                if not release_publish.wait(15):
                    raise AssertionError("Publish hold timed out")
        except Exception as exc:
            failures.append(exc)
            published.set()

    def archive():
        try:
            with psycopg.connect(DSN) as conn:
                rpc(conn, "ARCHIVE", first_id, "reviewer-a")
            archived.set()
        except Exception as exc:
            failures.append(exc)
            archived.set()

    publisher = threading.Thread(target=publish, daemon=True)
    archiver = threading.Thread(target=archive, daemon=True)
    publisher.start()
    if not published.wait(15) or failures:
        raise AssertionError(f"Publish failed before archive: {failures}")
    archiver.start()

    with connect() as observer:
        deadline = time.monotonic() + 10
        saw_waiter = False
        while time.monotonic() < deadline:
            saw_waiter = observer.execute("""
                select exists(select 1 from pg_locks
                    where locktype='advisory' and granted=false)
            """).fetchone()[0]
            if saw_waiter:
                break
            time.sleep(0.05)
        if not saw_waiter or archived.is_set():
            raise AssertionError("Archive did not wait on the shared transaction lock")

    release_publish.set()
    publisher.join(15)
    archiver.join(15)
    if publisher.is_alive() or archiver.is_alive() or failures:
        raise AssertionError(f"Concurrent operations failed: {failures}")
    with connect() as conn:
        assert_active_consistent(conn, first_id, second_id)
    print("PASS: archive waited for publish; final release hash, count and rows agree")


if __name__ == "__main__":
    main()
