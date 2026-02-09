ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_owner_id_fkey;
ALTER TABLE trips
  ADD CONSTRAINT trips_owner_id_fkey
  FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE trip_members DROP CONSTRAINT IF EXISTS trip_members_trip_id_fkey;
ALTER TABLE trip_members
  ADD CONSTRAINT trip_members_trip_id_fkey
  FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE;
ALTER TABLE trip_members DROP CONSTRAINT IF EXISTS trip_members_user_id_fkey;
ALTER TABLE trip_members
  ADD CONSTRAINT trip_members_user_id_fkey
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE trip_invite_links DROP CONSTRAINT IF EXISTS trip_invite_links_trip_id_fkey;
ALTER TABLE trip_invite_links
  ADD CONSTRAINT trip_invite_links_trip_id_fkey
  FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE;
ALTER TABLE trip_invite_links DROP CONSTRAINT IF EXISTS trip_invite_links_created_by_fkey;
ALTER TABLE trip_invite_links
  ADD CONSTRAINT trip_invite_links_created_by_fkey
  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ideas DROP CONSTRAINT IF EXISTS ideas_trip_id_fkey;
ALTER TABLE ideas
  ADD CONSTRAINT ideas_trip_id_fkey
  FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE;
ALTER TABLE ideas DROP CONSTRAINT IF EXISTS ideas_author_id_fkey;
ALTER TABLE ideas
  ADD CONSTRAINT ideas_author_id_fkey
  FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE idea_comments DROP CONSTRAINT IF EXISTS idea_comments_idea_id_fkey;
ALTER TABLE idea_comments
  ADD CONSTRAINT idea_comments_idea_id_fkey
  FOREIGN KEY (idea_id) REFERENCES ideas(id) ON DELETE CASCADE;
ALTER TABLE idea_comments DROP CONSTRAINT IF EXISTS idea_comments_author_id_fkey;
ALTER TABLE idea_comments
  ADD CONSTRAINT idea_comments_author_id_fkey
  FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE itinerary_days DROP CONSTRAINT IF EXISTS itinerary_days_trip_id_fkey;
ALTER TABLE itinerary_days
  ADD CONSTRAINT itinerary_days_trip_id_fkey
  FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE;

ALTER TABLE activities DROP CONSTRAINT IF EXISTS activities_day_id_fkey;
ALTER TABLE activities
  ADD CONSTRAINT activities_day_id_fkey
  FOREIGN KEY (day_id) REFERENCES itinerary_days(id) ON DELETE CASCADE;
ALTER TABLE activities DROP CONSTRAINT IF EXISTS activities_source_idea_id_fkey;
ALTER TABLE activities
  ADD CONSTRAINT activities_source_idea_id_fkey
  FOREIGN KEY (source_idea_id) REFERENCES ideas(id) ON DELETE SET NULL;

ALTER TABLE expenses DROP CONSTRAINT IF EXISTS expenses_trip_id_fkey;
ALTER TABLE expenses
  ADD CONSTRAINT expenses_trip_id_fkey
  FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE;
ALTER TABLE expenses DROP CONSTRAINT IF EXISTS expenses_paid_by_fkey;
ALTER TABLE expenses
  ADD CONSTRAINT expenses_paid_by_fkey
  FOREIGN KEY (paid_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE expense_splits DROP CONSTRAINT IF EXISTS expense_splits_expense_id_fkey;
ALTER TABLE expense_splits
  ADD CONSTRAINT expense_splits_expense_id_fkey
  FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE;
ALTER TABLE expense_splits DROP CONSTRAINT IF EXISTS expense_splits_user_id_fkey;
ALTER TABLE expense_splits
  ADD CONSTRAINT expense_splits_user_id_fkey
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE weather_forecasts DROP CONSTRAINT IF EXISTS weather_forecasts_trip_id_fkey;
ALTER TABLE weather_forecasts
  ADD CONSTRAINT weather_forecasts_trip_id_fkey
  FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE;

ALTER TABLE ai_requests DROP CONSTRAINT IF EXISTS ai_requests_trip_id_fkey;
ALTER TABLE ai_requests
  ADD CONSTRAINT ai_requests_trip_id_fkey
  FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE;
ALTER TABLE ai_requests DROP CONSTRAINT IF EXISTS ai_requests_created_by_fkey;
ALTER TABLE ai_requests
  ADD CONSTRAINT ai_requests_created_by_fkey
  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ai_suggestions DROP CONSTRAINT IF EXISTS ai_suggestions_request_id_fkey;
ALTER TABLE ai_suggestions
  ADD CONSTRAINT ai_suggestions_request_id_fkey
  FOREIGN KEY (request_id) REFERENCES ai_requests(id) ON DELETE CASCADE;
ALTER TABLE ai_suggestions DROP CONSTRAINT IF EXISTS ai_suggestions_saved_idea_id_fkey;
ALTER TABLE ai_suggestions
  ADD CONSTRAINT ai_suggestions_saved_idea_id_fkey
  FOREIGN KEY (saved_idea_id) REFERENCES ideas(id) ON DELETE SET NULL;

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_user_id_fkey;
ALTER TABLE notifications
  ADD CONSTRAINT notifications_user_id_fkey
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE notification_settings DROP CONSTRAINT IF EXISTS notification_settings_user_id_fkey;
ALTER TABLE notification_settings
  ADD CONSTRAINT notification_settings_user_id_fkey
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
