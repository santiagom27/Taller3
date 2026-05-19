package com.example.taller3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.taller3.databinding.ActivityHomeBinding
import com.example.taller3.model.Usuario
import com.example.taller3.utils.BitmapUtils
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class HomeActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var mMap: GoogleMap
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var myMarker: Marker? = null
    private var myPolyline: Polyline? = null
    private val myPolylinePoints = mutableListOf<LatLng>()

    // Marcadores y polylines de otros usuarios
    private val otrosMarkers = mutableMapOf<String, Marker>()
    private val otrosPolylines = mutableMapOf<String, Polyline>()
    private val otrosPuntos = mutableMapOf<String, MutableList<LatLng>>()

    private var usuariosListener: ValueEventListener? = null
    private var mapaCargado = false
    private var currentLocation: LatLng? = null

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 3000L
    ).setMinUpdateDistanceMeters(5f).build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val pos = LatLng(location.latitude, location.longitude)
                actualizarMiPosicion(pos)
            }
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            if (binding.switchLocalizacion.isChecked) iniciarLocalizacion()
        } else {
            binding.switchLocalizacion.isChecked = false
            Toast.makeText(this, getString(R.string.permiso_ubicacion_requerido), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.switchLocalizacion.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (tienePermisosUbicacion()) iniciarLocalizacion()
                else pedirPermisosUbicacion()
            } else {
                detenerLocalizacion()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_perfil -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mapaCargado = true

        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true

        // Estilo oscuro opcional
        try {
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))
        } catch (e: Exception) {
            // Si no existe el archivo de estilo, usa el default
        }

        escucharOtrosUsuarios()
    }

    private fun iniciarLocalizacion() {
        if (!tienePermisosUbicacion()) return

        database.child("usuarios").child(auth.currentUser!!.uid)
            .child("enLinea").setValue(true)

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun detenerLocalizacion() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        val uid = auth.currentUser?.uid ?: return

        database.child("usuarios").child(uid).child("enLinea").setValue(false)

        // Borrar polyline propia
        myPolyline?.remove()
        myPolyline = null
        myPolylinePoints.clear()
    }

    private fun actualizarMiPosicion(pos: LatLng) {
        val uid = auth.currentUser?.uid ?: return

        // Guardar en Firebase
        database.child("usuarios").child(uid).apply {
            child("latitud").setValue(pos.latitude)
            child("longitud").setValue(pos.longitude)
        }

        // Actualizar marcador en mapa
        if (myMarker == null) {
            cargarMarcadorUsuario(uid, pos, true)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
        } else {
            myMarker?.position = pos
        }

        // Polyline
        myPolylinePoints.add(pos)
        if (myPolylinePoints.size > 1) {
            myPolyline?.remove()
            myPolyline = mMap.addPolyline(
                PolylineOptions()
                    .addAll(myPolylinePoints)
                    .color(android.graphics.Color.parseColor("#4CAF50"))
                    .width(8f)
            )
        }
        currentLocation = pos
    }

    private fun escucharOtrosUsuarios() {
        val myUid = auth.currentUser?.uid ?: return

        usuariosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    if (uid == myUid) continue

                    val enLinea = child.child("enLinea").getValue(Boolean::class.java) ?: false
                    val lat = child.child("latitud").getValue(Double::class.java) ?: 0.0
                    val lng = child.child("longitud").getValue(Double::class.java) ?: 0.0

                    if (enLinea && lat != 0.0 && lng != 0.0) {
                        val pos = LatLng(lat, lng)

                        if (!otrosMarkers.containsKey(uid)) {
                            // Crear marcador nuevo
                            cargarMarcadorUsuario(uid, pos, false)
                            otrosPuntos[uid] = mutableListOf(pos)
                        } else {
                            // Actualizar posición
                            otrosMarkers[uid]?.position = pos
                            otrosPuntos[uid]?.add(pos)

                            val puntos = otrosPuntos[uid]!!
                            if (puntos.size > 1) {
                                otrosPolylines[uid]?.remove()
                                otrosPolylines[uid] = mMap.addPolyline(
                                    PolylineOptions()
                                        .addAll(puntos)
                                        .color(android.graphics.Color.parseColor("#2196F3"))
                                        .width(8f)
                                )
                            }
                        }
                    } else {
                        // Usuario desconectado: limpiar
                        otrosMarkers[uid]?.remove()
                        otrosMarkers.remove(uid)
                        otrosPolylines[uid]?.remove()
                        otrosPolylines.remove(uid)
                        otrosPuntos.remove(uid)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, error.message, Toast.LENGTH_SHORT).show()
            }
        }

        database.child("usuarios").limitToFirst(100)
            .addValueEventListener(usuariosListener!!)
    }

    private fun cargarMarcadorUsuario(uid: String, pos: LatLng, esMio: Boolean) {
        database.child("usuarios").child(uid).get().addOnSuccessListener { snap ->
            val nombre = snap.child("nombre").getValue(String::class.java) ?: "?"
            val fotoUrl = snap.child("fotoPerfil").getValue(String::class.java) ?: ""

            if (fotoUrl.isNotEmpty()) {
                Glide.with(this)
                    .asBitmap()
                    .load(fotoUrl)
                    .override(120, 120)
                    .circleCrop()
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            val markerBitmap = BitmapUtils.createCustomMarkerFromBitmap(this@HomeActivity, resource, esMio)
                            agregarMarcador(uid, pos, nombre, markerBitmap, esMio)
                        }
                        override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                    })
            } else {
                val inicial = nombre.firstOrNull()?.toString() ?: "?"
                val markerBitmap = BitmapUtils.createInitialMarker(this@HomeActivity, inicial, esMio)
                agregarMarcador(uid, pos, nombre, markerBitmap, esMio)
            }
        }
    }

    private fun agregarMarcador(uid: String, pos: LatLng, nombre: String, bitmap: Bitmap, esMio: Boolean) {
        val options = MarkerOptions()
            .position(pos)
            .title(nombre)
            .icon(BitmapDescriptorFactory.fromBitmap(bitmap))

        val marker = mMap.addMarker(options)
        if (esMio) {
            myMarker = marker
        } else {
            otrosMarkers[uid] = marker!!
        }
    }

    private fun tienePermisosUbicacion(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun pedirPermisosUbicacion() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun logout() {
        detenerLocalizacion()
        usuariosListener?.let {
            database.child("usuarios").removeEventListener(it)
        }
        auth.signOut()
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        usuariosListener?.let {
            database.child("usuarios").removeEventListener(it)
        }
    }
}