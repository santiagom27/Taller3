package com.example.taller3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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

    private val otrosMarkers = mutableMapOf<String, Marker>()
    private val otrosPolylines = mutableMapOf<String, Polyline>()
    private val otrosPuntos = mutableMapOf<String, MutableList<LatLng>>()
    private val marcadoresCargando = mutableSetOf<String>()

    private var usuariosListener: ValueEventListener? = null
    private var mapaCargado = false

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        3000L
    )
        .setMinUpdateDistanceMeters(5f)
        .build()

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
        val granted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
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

        if (auth.currentUser == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

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
        menu.findItem(R.id.action_perfil)?.icon?.setTint(
            ContextCompat.getColor(this, android.R.color.white)
        )
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

        try {
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))
        } catch (_: Exception) {}

        escucharUsuarios()
    }

    private fun iniciarLocalizacion() {
        val uid = auth.currentUser?.uid ?: return
        if (!tienePermisosUbicacion()) { pedirPermisosUbicacion(); return }

        database.child("usuarios").child(uid).child("enLinea").setValue(true)

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    actualizarMiPosicion(LatLng(location.latitude, location.longitude))
                } else {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { current ->
                            current?.let { actualizarMiPosicion(LatLng(it.latitude, it.longitude)) }
                        }
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun detenerLocalizacion() {
        val uid = auth.currentUser?.uid ?: return

        fusedLocationClient.removeLocationUpdates(locationCallback)

        database.child("usuarios").child(uid).child("enLinea").setValue(false)

        myMarker?.remove()
        myMarker = null
        marcadoresCargando.remove(uid)

        myPolyline?.remove()
        myPolyline = null
        myPolylinePoints.clear()
    }

    private fun actualizarMiPosicion(pos: LatLng) {
        if (!mapaCargado) return
        val uid = auth.currentUser?.uid ?: return

        database.child("usuarios").child(uid).updateChildren(
            mapOf("latitud" to pos.latitude, "longitud" to pos.longitude, "enLinea" to true)
        )

        if (myMarker == null) {
            cargarMarcadorUsuario(uid, pos, true)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))
        } else {
            myMarker?.position = pos
        }

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
    }

    private fun escucharUsuarios() {
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
                            if (!marcadoresCargando.contains(uid)) {
                                marcadoresCargando.add(uid)
                                otrosPuntos[uid] = mutableListOf(pos)
                                cargarMarcadorUsuario(uid, pos, false)
                            } else {
                                otrosPuntos[uid]?.add(pos)
                            }
                        } else {
                            otrosMarkers[uid]?.position = pos
                            val puntos = otrosPuntos.getOrPut(uid) { mutableListOf() }
                            puntos.add(pos)
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
                        limpiarUsuario(uid)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, error.message, Toast.LENGTH_SHORT).show()
            }
        }

        database.child("usuarios").addValueEventListener(usuariosListener!!)
    }

    // ─── ÚNICO CAMBIO RESPECTO A LA VERSIÓN ANTERIOR ───────────────────────────
    // Antes: leía fotoBase64 y llamaba ImageUtils.base64ToBitmap()
    // Ahora: lee fotoUrl (String con https://...) y usa Glide para descargar el Bitmap
    private fun cargarMarcadorUsuario(uid: String, pos: LatLng, esMio: Boolean) {
        database.child("usuarios").child(uid).get()
            .addOnSuccessListener { snap ->
                val nombre = snap.child("nombre").getValue(String::class.java) ?: "?"
                val fotoUrl = snap.child("fotoPerfil").getValue(String::class.java) ?: ""

                if (fotoUrl.isNotEmpty()) {
                    Glide.with(applicationContext)
                        .asBitmap()
                        .load(fotoUrl)
                        .into(object : CustomTarget<Bitmap>() {
                            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                val markerBitmap = BitmapUtils.createCustomMarkerFromBitmap(
                                    this@HomeActivity, resource, esMio
                                )
                                agregarMarcador(uid, pos, nombre, markerBitmap, esMio)
                                if (!esMio) marcadoresCargando.remove(uid)
                            }

                            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}

                            override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                                // Si Glide falla, usar marcador de inicial
                                val inicial = nombre.firstOrNull()?.toString() ?: "?"
                                val markerBitmap = BitmapUtils.createInitialMarker(this@HomeActivity, inicial, esMio)
                                agregarMarcador(uid, pos, nombre, markerBitmap, esMio)
                                if (!esMio) marcadoresCargando.remove(uid)
                            }
                        })
                } else {
                    val inicial = nombre.firstOrNull()?.toString() ?: "?"
                    val markerBitmap = BitmapUtils.createInitialMarker(this, inicial, esMio)
                    agregarMarcador(uid, pos, nombre, markerBitmap, esMio)
                    if (!esMio) marcadoresCargando.remove(uid)
                }
            }
            .addOnFailureListener {
                if (!esMio) marcadoresCargando.remove(uid)
            }
    }
    // ───────────────────────────────────────────────────────────────────────────

    private fun agregarMarcador(uid: String, pos: LatLng, nombre: String, bitmap: Bitmap, esMio: Boolean) {
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(pos)
                .title(nombre)
                .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
        ) ?: return

        if (esMio) myMarker = marker
        else otrosMarkers[uid] = marker
    }

    private fun limpiarUsuario(uid: String) {
        otrosMarkers[uid]?.remove()
        otrosMarkers.remove(uid)
        otrosPolylines[uid]?.remove()
        otrosPolylines.remove(uid)
        otrosPuntos.remove(uid)
        marcadoresCargando.remove(uid)
    }

    private fun tienePermisosUbicacion(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun pedirPermisosUbicacion() {
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun logout() {
        detenerLocalizacion()
        usuariosListener?.let { database.child("usuarios").removeEventListener(it) }
        auth.signOut()
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }

    override fun onDestroy() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            database.child("usuarios").child(uid).child("enLinea").setValue(false)
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
        usuariosListener?.let { database.child("usuarios").removeEventListener(it) }
        super.onDestroy()
    }
}