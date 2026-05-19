package com.example.taller3

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.taller3.databinding.ActivityProfileBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private var nuevaFotoUri: Uri? = null
    private var cameraUri: Uri? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            nuevaFotoUri = it
            binding.imgFotoPerfil.setImageURI(it)
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            nuevaFotoUri = cameraUri
            binding.imgFotoPerfil.setImageURI(cameraUri)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, getString(R.string.permiso_camara_requerido), Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = getString(R.string.mi_perfil)
            setDisplayHomeAsUpEnabled(true)
        }

        auth = FirebaseAuth.getInstance()
        cargarDatosUsuario()

        binding.imgFotoPerfil.setOnClickListener { mostrarOpcionesFoto() }
        binding.btnCambiarFoto.setOnClickListener { mostrarOpcionesFoto() }

        binding.btnGuardarNombreTelefono.setOnClickListener {
            guardarNombreTelefono()
        }

        binding.btnCambiarPassword.setOnClickListener {
            cambiarPassword()
        }
    }

    private fun cargarDatosUsuario() {
        val uid = auth.currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference
            .child("usuarios").child(uid)
            .get()
            .addOnSuccessListener { snap ->
                binding.etNombre.setText(snap.child("nombre").getValue(String::class.java) ?: "")
                binding.etTelefono.setText(snap.child("telefono").getValue(String::class.java) ?: "")
                binding.tvEmail.text = snap.child("email").getValue(String::class.java) ?: ""
                val fotoUrl = snap.child("fotoPerfil").getValue(String::class.java) ?: ""
                if (fotoUrl.isNotEmpty()) {
                    Glide.with(this).load(fotoUrl).circleCrop().into(binding.imgFotoPerfil)
                }
            }
    }

    private fun mostrarOpcionesFoto() {
        val opciones = arrayOf(getString(R.string.tomar_foto), getString(R.string.elegir_galeria))
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.foto_perfil))
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) launchCamera()
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    1 -> galleryLauncher.launch("image/*")
                }
            }.show()
    }

    private fun launchCamera() {
        val imgFile = File(cacheDir, "foto_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(this, "${packageName}.provider", imgFile)
        cameraLauncher.launch(cameraUri)
    }

    private fun guardarNombreTelefono() {
        val uid = auth.currentUser?.uid ?: return
        val nombre = binding.etNombre.text.toString().trim()
        val telefono = binding.etTelefono.text.toString().trim()

        if (nombre.isEmpty() || telefono.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_campos_vacios), Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mutableMapOf<String, Any>(
            "nombre" to nombre,
            "telefono" to telefono
        )

        binding.btnGuardarNombreTelefono.isEnabled = false

        // Si hay foto nueva, subirla primero
        if (nuevaFotoUri != null) {
            val storageRef = FirebaseStorage.getInstance().reference.child("fotos_perfil/$uid.jpg")
            storageRef.putFile(nuevaFotoUri!!)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        updates["fotoPerfil"] = downloadUri.toString()
                        guardarEnDB(uid, updates)
                    }
                }
                .addOnFailureListener { guardarEnDB(uid, updates) }
        } else {
            guardarEnDB(uid, updates)
        }
    }

    private fun guardarEnDB(uid: String, updates: Map<String, Any>) {
        FirebaseDatabase.getInstance().reference
            .child("usuarios").child(uid)
            .updateChildren(updates)
            .addOnSuccessListener {
                binding.btnGuardarNombreTelefono.isEnabled = true
                Toast.makeText(this, getString(R.string.datos_actualizados), Toast.LENGTH_SHORT).show()
                nuevaFotoUri = null
            }
            .addOnFailureListener { e ->
                binding.btnGuardarNombreTelefono.isEnabled = true
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun cambiarPassword() {
        val passwordActual = binding.etPasswordActual.text.toString().trim()
        val passwordNueva = binding.etPasswordNueva.text.toString().trim()

        if (passwordActual.isEmpty() || passwordNueva.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_campos_vacios), Toast.LENGTH_SHORT).show()
            return
        }
        if (passwordNueva.length < 6) {
            Toast.makeText(this, getString(R.string.error_password_corta), Toast.LENGTH_SHORT).show()
            return
        }

        val user = auth.currentUser ?: return
        val credential = EmailAuthProvider.getCredential(user.email!!, passwordActual)

        binding.btnCambiarPassword.isEnabled = false
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(passwordNueva)
                    .addOnSuccessListener {
                        binding.btnCambiarPassword.isEnabled = true
                        binding.etPasswordActual.text?.clear()
                        binding.etPasswordNueva.text?.clear()
                        Toast.makeText(this, getString(R.string.password_actualizada), Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        binding.btnCambiarPassword.isEnabled = true
                        Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.btnCambiarPassword.isEnabled = true
                Toast.makeText(this, getString(R.string.error_reautenticacion, e.message), Toast.LENGTH_LONG).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}