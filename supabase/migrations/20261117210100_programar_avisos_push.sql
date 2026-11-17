-- Extensions and schedule for push notifications (see 20261117210000_notificaciones_push.sql):
-- pg_net lets the database call the send-push Edge Function, and pg_cron checks every minute for
-- activities that are starting and shared statuses that just expired.
create extension if not exists pg_net;
create extension if not exists pg_cron;
select cron.schedule('near-push-due-events', '* * * * *', $job$
    select private.push_due_events();
    delete from cron.job_run_details
    where jobid = (select jobid from cron.job where jobname = 'near-push-due-events')
      and end_time < now() - interval '1 day';
$job$);
