-- Optional Supabase-side warm ping for the Render free-tier backend.
--
-- GitHub Actions keepalive runs every 6 hours (free-minute budget), so the
-- instance still sleeps between runs. This job pings the liveness endpoint
-- every 10 minutes from inside Postgres, costing zero CI minutes. It is a
-- no-op unless BOTH pg_cron and pg_net are enabled in the Supabase project
-- (Dashboard -> Database -> Extensions), so it is safe to apply everywhere.

DO $$
BEGIN
  IF to_regnamespace('cron') IS NULL
     OR to_regproc('cron.schedule') IS NULL
     OR to_regproc('cron.unschedule') IS NULL
     OR to_regproc('net.http_get') IS NULL THEN
    RAISE NOTICE 'pg_cron/pg_net not installed; render warm ping skipped';
    RETURN;
  END IF;

  BEGIN
    PERFORM cron.unschedule('render_keepalive_warm_ping');
  EXCEPTION WHEN OTHERS THEN
    -- Job not registered yet; nothing to unschedule.
  END;

  PERFORM cron.schedule(
    'render_keepalive_warm_ping',
    '*/10 * * * *',
    $job$SELECT net.http_get('https://campuscore-backend-p4em.onrender.com/api/v1/health/liveness'::text)$job$
  );
END
$$;
