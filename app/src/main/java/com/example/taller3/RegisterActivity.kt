package com.example.taller3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.taller3.databinding.ActivityRegisterBinding
import com.example.taller3.model.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private var fotoUri: Uri? = null
    private var cameraUri: Uri? = null

    // Lanzador para seleccionar imagen de galería
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            fotoUri = it
            binding.imgFotoPerfil.setImageURI(it)
        }
    }

    // Lanzador para tomar foto con cámara
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            fotoUri = cameraUri
            binding.imgFotoPerfil.setImageURI(cameraUri)
        }
    }

    // Permisos de cámara
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, getString(R.string.permiso_camara_requerido), Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.imgFotoPerfil.setOnClickListener { mostrarOpcionesFoto() }
        binding.btnFoto.setOnClickListener { mostrarOpcionesFoto() }

        binding.btnRegistrar.setOnClickListener {
            val nombre = binding.etNombre.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val telefono = binding.etTelefono.text.toString().trim()

            if (nombre.isEmpty() || email.isEmpty() || password.isEmpty() || telefono.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_campos_vacios), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, getString(R.string.error_password_corta), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnRegistrar.isEnabled = false
            registrarUsuario(nombre, email, password, telefono)
        }

        binding.tvVolver.setOnClickListener { finish() }
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
        val imgFile = File(cacheDir, "foto_perfil_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(this, "${packageName}.provider", imgFile)
        cameraLauncher.launch(cameraUri)
    }

    private fun registrarUsuario(nombre: String, email: String, password: String, telefono: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                if (fotoUri != null) {
                    subirFotoYGuardarUsuario(uid, nombre, email, telefono, fotoUri!!)
                } else {
                    guardarUsuarioEnDB(uid, nombre, email, telefono, "")
                }
            }
            .addOnFailureListener { e ->
                binding.btnRegistrar.isEnabled = true
                Toast.makeText(this, getString(R.string.error_registro, e.message), Toast.LENGTH_LONG).show()
            }
    }

    private fun subirFotoYGuardarUsuario(uid: String, nombre: String, email: String, telefono: String, uri: Uri) {
        val storageRef = FirebaseStorage.getInstance().reference.child("fotos_perfil/$uid.jpg")
        storageRef.putFile(uri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    guardarUsuarioEnDB(uid, nombre, email, telefono, downloadUri.toString())
                }
            }
            .addOnFailureListener {
                // Guardar sin foto si falla el storage
                guardarUsuarioEnDB(uid, nombre, email, telefono, "")
            }
    }

    private fun guardarUsuarioEnDB(uid: String, nombre: String, email: String, telefono: String, fotoUrl: String) {
        val usuario = Usuario(
            nombre = nombre,
            email = email,
            telefono = telefono,
            enLinea = false,
            latitud = 0.0,
            longitud = 0.0,
            fotoPerfil = fotoUrl
        )
        FirebaseDatabase.getInstance().reference
            .child("usuarios")
            .child(uid)
            .setValue(usuario)
            .addOnSuccessListener {
                Toast.makeText(this, getString(R.string.registro_exitoso), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                binding.btnRegistrar.isEnabled = true
                Toast.makeText(this, getString(R.string.error_guardar_datos, e.message), Toast.LENGTH_LONG).show()
            }
    }
}