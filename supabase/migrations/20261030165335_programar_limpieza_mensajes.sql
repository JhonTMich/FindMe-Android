create extension if not exists pg_cron;
select cron.schedule('findme-purge-messages', '*/5 * * * *', $job$
    select private.purge_expired_signals();
    delete from cron.job_run_details
    where jobid = (select jobid from cron.job where jobname = 'findme-purge-messages')
      and end_time < now() - interval '7 days';
$job$);
