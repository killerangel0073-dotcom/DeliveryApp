package com.gruposanangel.delivery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ClienteEntity::class,
        ProductoEntity::class,
        VentaEntity::class,
        VentaDetalleEntity::class,
        UsuarioEntity::class,
        LocationEntity::class,
        RutaEntity::class,
        MovimientoInventarioEntity::class
    ],
    version = 22,
    exportSchema = true // Habilitado para soportar migraciones automáticas
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clienteDao(): ClienteDao
    abstract fun productoDao(): ProductoDao
    abstract fun VentaDao(): VentaDao
    abstract fun usuarioDao(): UsuarioDao
    abstract fun locationDao(): LocationDao
    abstract fun rutaDao(): RutaDao
    abstract fun movimientoInventarioDao(): MovimientoInventarioDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mi_base_de_datos"
                )
                    .fallbackToDestructiveMigration() // 🔥 REHABILITADO: Permite recrear la DB si el esquema cambia
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
