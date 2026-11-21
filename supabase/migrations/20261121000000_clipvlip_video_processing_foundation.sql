-- ClipVlip Supabase foundation
-- Access model: the Python worker uses the Supabase secret key server-side.
-- No anon/authenticated table or storage policies are created; direct client access is denied.

create extension if not exists pgcrypto;

create table public.video_jobs (
  id text primary key,
  title text not null default 'Untitled project',
  source_type text not null check (source_type in ('upload','url')),
  source_path text,
  source_url text,
  status text not null default 'queued' check (status in ('queued','processing','completed','failed','cancelled')),
  progress integer not null default 0 check (progress between 0 and 100),
  message text not null default 'Waiting to start',
  settings jsonb not null default '{}'::jsonb,
  error text,
  created_at timestamptz not null default now(),
  started_at timestamptz,
  completed_at timestamptz,
  updated_at timestamptz not null default now()
);

create index video_jobs_created_at_idx on public.video_jobs (created_at desc);
create index video_jobs_status_created_idx on public.video_jobs (status, created_at);

create table public.video_assets (
  id uuid primary key default gen_random_uuid(),
  job_id text not null references public.video_jobs(id) on delete cascade,
  asset_type text not null check (asset_type in ('source','clip','subtitle','thumbnail')),
  storage_path text not null,
  filename text not null,
  content_type text,
  size_bytes bigint check (size_bytes is null or size_bytes >= 0),
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  unique (job_id, storage_path)
);
create index video_assets_job_idx on public.video_assets (job_id, created_at);

create table public.video_clips (
  id uuid primary key default gen_random_uuid(),
  job_id text not null references public.video_jobs(id) on delete cascade,
  clip_index integer not null check (clip_index > 0),
  title text not null,
  reason text,
  start_seconds double precision not null check (start_seconds >= 0),
  end_seconds double precision not null check (end_seconds > start_seconds),
  score double precision,
  asset_path text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  unique (job_id, clip_index)
);
create index video_clips_job_idx on public.video_clips (job_id, clip_index);

alter table public.video_jobs enable row level security;
alter table public.video_assets enable row level security;
alter table public.video_clips enable row level security;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('clipvlip-sources', 'clipvlip-sources', false, 4294967296, array['video/mp4','video/quicktime','video/x-matroska','video/webm','video/x-msvideo']),
  ('clipvlip-outputs', 'clipvlip-outputs', false, 1073741824, array['video/mp4','text/plain','application/octet-stream'])
on conflict (id) do nothing;

create or replace function public.set_video_job_updated_at()
returns trigger language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger video_jobs_updated_at
before update on public.video_jobs
for each row execute function public.set_video_job_updated_at();
