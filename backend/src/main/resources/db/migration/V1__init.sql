CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  google_id text UNIQUE NOT NULL,
  name text NOT NULL,
  photo_url text,
  created_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE TABLE trips (
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

CREATE TABLE trip_members (
  trip_id uuid NOT NULL REFERENCES trips(id),
  user_id uuid NOT NULL REFERENCES users(id),
  role text NOT NULL CHECK (role IN ('owner', 'member')),
  status text NOT NULL CHECK (status IN ('invited', 'accepted', 'declined')),
  joined_at timestamptz,
  PRIMARY KEY (trip_id, user_id)
);

CREATE TABLE trip_invite_links (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id),
  token text UNIQUE NOT NULL,
  expires_at timestamptz NOT NULL,
  created_by uuid NOT NULL REFERENCES users(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz,
  uses_count int NOT NULL DEFAULT 0
);

CREATE INDEX idx_trip_invite_links_trip_id ON trip_invite_links(trip_id);

CREATE TABLE ideas (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id),
  author_id uuid NOT NULL REFERENCES users(id),
  title text NOT NULL,
  city text,
  cost_amount numeric,
  cost_type text CHECK (cost_type IN ('per_person', 'total')),
  website text,
  notes text,
  status text NOT NULL CHECK (status IN ('pending', 'approved', 'rejected')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE INDEX idx_ideas_trip_status ON ideas(trip_id, status);

CREATE TABLE idea_comments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  idea_id uuid NOT NULL REFERENCES ideas(id),
  author_id uuid NOT NULL REFERENCES users(id),
  type text NOT NULL CHECK (type IN ('user', 'system')),
  body text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE INDEX idx_idea_comments_idea_time ON idea_comments(idea_id, created_at);

CREATE TABLE itinerary_days (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trip_id uuid NOT NULL REFERENCES trips(id),
  date date NOT NULL,
  day_number int NOT NULL,
  city text,
  is_out_of_range boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_itinerary_days_trip_date ON itinerary_days(trip_id, date);

CREATE TABLE activities (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  day_id uuid NOT NULL REFERENCES itinerary_days(id),
  source_idea_id uuid REFERENCES ideas(id),
  title text NOT NULL,
  time_text text,
  location_name text,
  location_link text,
  cost_amount numeric,
  cost_type text CHECK (cost_type IN ('per_person', 'total')),
  website text,
  notes text,
  order_index int NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE INDEX idx_activities_day_order ON activities(day_id, order_index);

CREATE TABLE expenses (
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

CREATE INDEX idx_expenses_trip_status ON expenses(trip_id, status);

CREATE TABLE expense_splits (
  expense_id uuid NOT NULL REFERENCES expenses(id),
  user_id uuid NOT NULL REFERENCES users(id),
  share_amount numeric,
  is_included boolean NOT NULL DEFAULT true,
  is_paid boolean NOT NULL DEFAULT false,
  PRIMARY KEY (expense_id, user_id)
);

CREATE TABLE weather_forecasts (
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

CREATE TABLE ai_requests (
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

CREATE TABLE ai_suggestions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id uuid NOT NULL REFERENCES ai_requests(id),
  title text NOT NULL,
  description text,
  type_label text,
  duration_label text,
  budget_label text,
  estimated_cost numeric,
  is_saved boolean NOT NULL DEFAULT false,
  saved_idea_id uuid REFERENCES ideas(id),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id),
  type text NOT NULL,
  payload jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  read_at timestamptz
);

CREATE INDEX idx_notifications_user_read ON notifications(user_id, read_at);

CREATE TABLE notification_settings (
  user_id uuid NOT NULL REFERENCES users(id),
  key text NOT NULL,
  enabled boolean NOT NULL,
  PRIMARY KEY (user_id, key)
);
