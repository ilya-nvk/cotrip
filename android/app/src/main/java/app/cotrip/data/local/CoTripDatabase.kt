package app.cotrip.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TripEntity::class], version = 1, exportSchema = false)
abstract class CoTripDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
}
