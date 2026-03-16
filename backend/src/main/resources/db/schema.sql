CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  google_id text UNIQUE NOT NULL,
  name text NOT NULL,
  photo_url text,
  created_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE TABLE IF NOT EXISTS trips (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id uuid NOT NULL REFERENCES users(id),
  title text NOT NULL,
  description text,
  start_date date NOT NULL,
  end_date date NOT NULL,
  location_line text,
  cover_url text,
  currency_code text NOT NULL,
  status text NOT NULL CHECK (status IN ('active', 'archived')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE TABLE IF NOT EXISTS trip_members (
  trip_id uuid NOT NULL REFERENCES trips(id),
  user_id uuid NOT NULL REFERENCES users(id),
  role text NOT NULL CHECK (role IN ('owner', 'member')),
  status text NOT NULL CHECK (status IN ('invited', 'accepted', 'declined')),
  joined_at timestamptz,
  PRIMARY KEY (trip_id, user_id)
);

CREATE TABLE IF NOT EXISTS trip_invite_links (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id),
  token text UNIQUE NOT NULL,
  expires_at timestamptz NOT NULL,
  created_by uuid NOT NULL REFERENCES users(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz,
  uses_count int NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_trip_invite_links_trip_id ON trip_invite_links(trip_id);

CREATE TABLE IF NOT EXISTS ideas (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id),
  author_id uuid NOT NULL REFERENCES users(id),
  title text NOT NULL,
  city text,
  link text,
  cost_amount numeric,
  cost_type text CHECK (cost_type IN ('per_person', 'total')),
  website text,
  notes text,
  status text NOT NULL CHECK (status IN ('pending', 'approved', 'rejected')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_ideas_trip_status ON ideas(trip_id, status);

CREATE TABLE IF NOT EXISTS idea_comments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  idea_id uuid NOT NULL REFERENCES ideas(id),
  author_id uuid NOT NULL REFERENCES users(id),
  type text NOT NULL CHECK (type IN ('user', 'system')),
  body text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_idea_comments_idea_time ON idea_comments(idea_id, created_at);

CREATE TABLE IF NOT EXISTS itinerary_days (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id),
  date date NOT NULL,
  day_number int NOT NULL,
  city text,
  city_provider_id text,
  city_lat double precision,
  city_lon double precision,
  is_out_of_range boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_itinerary_days_trip_date ON itinerary_days(trip_id, date);

CREATE TABLE IF NOT EXISTS activities (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  day_id uuid NOT NULL REFERENCES itinerary_days(id),
  source_idea_id uuid REFERENCES ideas(id),
  title text NOT NULL,
  time_text text,
  location_name text,
  link text,
  cost_amount numeric,
  cost_type text CHECK (cost_type IN ('per_person', 'total')),
  website text,
  notes text,
  order_index int NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_activities_day_order ON activities(day_id, order_index);

CREATE TABLE IF NOT EXISTS expenses (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id),
  title text NOT NULL,
  amount numeric NOT NULL,
  currency_code text NOT NULL,
  status text NOT NULL CHECK (status IN ('planned', 'paid')),
  paid_by uuid REFERENCES users(id),
  expense_date date,
  split_type text NOT NULL CHECK (split_type IN ('equally', 'custom')),
  note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_expenses_trip_status ON expenses(trip_id, status);

CREATE TABLE IF NOT EXISTS expense_splits (
  expense_id uuid NOT NULL REFERENCES expenses(id),
  user_id uuid NOT NULL REFERENCES users(id),
  share_amount numeric,
  is_included boolean NOT NULL DEFAULT true,
  is_paid boolean NOT NULL DEFAULT false,
  PRIMARY KEY (expense_id, user_id)
);

CREATE TABLE IF NOT EXISTS weather_forecasts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id),
  city text NOT NULL,
  date date NOT NULL,
  temp_min numeric,
  temp_max numeric,
  description text,
  icon_code text,
  source text NOT NULL,
  fetched_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (trip_id, city, date)
);

CREATE TABLE IF NOT EXISTS ai_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id),
  city text,
  description text,
  type_options jsonb,
  time_of_day_options jsonb,
  budget_options jsonb,
  provider text NOT NULL,
  created_by uuid NOT NULL REFERENCES users(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  status text NOT NULL CHECK (status IN ('pending', 'done', 'error')),
  error text
);

CREATE TABLE IF NOT EXISTS ai_suggestions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id uuid NOT NULL REFERENCES ai_requests(id),
  title text NOT NULL,
  place text,
  description text,
  type_label text,
  duration_label text,
  budget_label text,
  estimated_cost numeric,
  is_saved boolean NOT NULL DEFAULT false,
  saved_idea_id uuid REFERENCES ideas(id),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS notifications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id),
  type text NOT NULL,
  payload jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  read_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON notifications(user_id, read_at);
CREATE INDEX IF NOT EXISTS idx_notifications_unread_comment_idea
  ON notifications(user_id, ((payload ->> 'ideaId')))
  WHERE type = 'idea_comment' AND read_at IS NULL;

CREATE TABLE IF NOT EXISTS notification_settings (
  user_id uuid NOT NULL REFERENCES users(id),
  key text NOT NULL,
  enabled boolean NOT NULL,
  PRIMARY KEY (user_id, key)
);

CREATE TABLE IF NOT EXISTS push_tokens (
  token text PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  platform text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_push_tokens_user_id ON push_tokens(user_id);

CREATE TABLE IF NOT EXISTS auth_sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  revoke_reason text
);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_active
  ON auth_sessions(user_id, created_at DESC)
  WHERE revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id uuid NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
  token_hash text UNIQUE NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  revoked_at timestamptz,
  replaced_by uuid REFERENCES auth_refresh_tokens(id)
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_session_id ON auth_refresh_tokens(session_id);
CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_token_hash ON auth_refresh_tokens(token_hash);
