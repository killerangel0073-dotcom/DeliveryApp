package com.gruposanangel.delivery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ClienteEntity::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clienteDao(): ClienteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mi_base_de_datos" // <- nombre de la base de datos
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
