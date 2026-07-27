export const SUPABASE_DASHBOARD_URL = "https://supabase.com/dashboard";

export const SUPABASE_SQL = `create table if not exists public.money_snapshots (
  user_id uuid primary key references auth.users(id) on delete cascade,
  data jsonb not null,
  updated_at timestamptz not null default now()
);

alter table public.money_snapshots enable row level security;

revoke all on table public.money_snapshots from anon;
grant select, insert, update, delete on table public.money_snapshots to authenticated;

drop policy if exists "snapshot_select_own" on public.money_snapshots;
create policy "snapshot_select_own"
on public.money_snapshots
for select
to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists "snapshot_insert_own" on public.money_snapshots;
create policy "snapshot_insert_own"
on public.money_snapshots
for insert
to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists "snapshot_update_own" on public.money_snapshots;
create policy "snapshot_update_own"
on public.money_snapshots
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "snapshot_delete_own" on public.money_snapshots;
create policy "snapshot_delete_own"
on public.money_snapshots
for delete
to authenticated
using ((select auth.uid()) = user_id);

create or replace function public.touch_money_snapshot()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists money_snapshot_updated_at on public.money_snapshots;
create trigger money_snapshot_updated_at
before update on public.money_snapshots
for each row execute function public.touch_money_snapshot();`;
