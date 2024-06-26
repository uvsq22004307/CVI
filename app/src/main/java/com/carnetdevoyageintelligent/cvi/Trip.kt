package com.carnetdevoyageintelligent.cvi


import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.media.ExifInterface
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage



class Trip : Fragment() {
    private lateinit var viewFragment: View
    private val tripList = mutableListOf<String>()
    private var tripName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_trip, container, false)
        viewFragment = view // Assigner la vue inflatée à la variable viewFragment
        return view
    }

    @SuppressLint("NotifyDataSetChanged", "CutPasteId")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val addButton: AppCompatImageButton = view.findViewById(R.id.add_trip_button)
        addButton.setOnClickListener {
            addTripDialog()
        }
        val refreshButton: AppCompatImageButton = view.findViewById(R.id.refresh_trip_button)
        refreshButton.setOnClickListener {
            refreshFragment()
        }

        val recyclerView: RecyclerView = view.findViewById(R.id.tripRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = TripAdapter(
            tripList,
            { tripName, anchorView -> showPopupMenu(tripName, anchorView) },
            recyclerView,
            this::previewPhotosClick,
            this::addPhotosClick,
            )

        recyclerView.adapter = adapter
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference
        storageRef.listAll()
            .addOnSuccessListener { listResult ->
                // Parcourir la liste des dossiers et récupérer leur nom
                val folderNames = listResult.prefixes.map { it.name }

                // Mettre à jour la liste de données de l'adaptateur avec les noms des dossiers
                tripList.clear()
                tripList.addAll(folderNames)

                // Notifier l'adaptateur du changement de données sur le thread principal
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                // Gérer les erreurs de récupération des dossiers
                Log.e(TAG, "Error retrieving folder names: ${exception.message}")
            }
    }

    private fun addTripDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Ajout d'un nouveau voyage")
        builder.setMessage("Déterminer le nom du voyage")
        val editTextTripName = EditText(requireContext())
        builder.setView(editTextTripName)
        builder.setNeutralButton("Ajouter un voyage") { dialog, _ ->
            tripName = editTextTripName.text.toString() // Assigner le nom du voyage ici
            if (tripName!!.isNotEmpty()) {
                dialog.dismiss() // Fermer la fenêtre
                openGalleryForPhotos()
            } else {
                // Afficher un message d'erreur si le champ est vide
                Toast.makeText(
                    requireContext(),
                    "Veuillez saisir un nom de voyage",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        builder.setNegativeButton("Annuler") { dialog, _ ->
            dialog.dismiss() // Fermer la fenêtre
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun renameTripDialog(){
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Renommer le voyage")
        builder.setMessage("Déterminer le nom du voyage")
        val editTextTripRename = EditText(requireContext())
        builder.setView(editTextTripRename)
        builder.setNeutralButton("Renommer") { dialog, _ ->
            tripName = editTextTripRename.text.toString() // Assigner le nom du voyage ici
            if (tripName!!.isNotEmpty()) {
                dialog.dismiss() // Fermer la fenêtre
            } else {
                // Afficher un message d'erreur si le champ est vide
                Toast.makeText(
                    requireContext(),
                    "Veuillez saisir un nom de voyage",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        builder.setNegativeButton("Annuler") { dialog, _ ->
            dialog.dismiss() // Fermer la fenêtre
        }
        val dialog = builder.create()
        dialog.show()
    }

    private val pickImages =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "démarrage pickImages")
                val data = result.data
                // Traitement des données retournées par l'activité de sélection d'images
                data?.let {
                    val selectedImagesUriList = mutableListOf<Uri>()
                    if (it.clipData != null) {
                        // Plusieurs images sélectionnées
                        val clipData = it.clipData
                        for (i in 0 until clipData!!.itemCount) {
                            val uri = clipData.getItemAt(i).uri
                            selectedImagesUriList.add(uri)
                        }
                    } else if (it.data != null) {
                        // Une seule image sélectionnée
                        val uri = it.data
                        selectedImagesUriList.add(uri!!)
                    }
                    Log.d(TAG, "$selectedImagesUriList")
                    // Télécharger les images dans Firebase Storage
                    tripName?.let { name ->
                        uploadImagesToFirebase(selectedImagesUriList, name)
                        Log.d(TAG, "upload en cours")
                    }
                }
            }
        }

    private fun openGalleryForPhotos() {
        Log.d(TAG, "ouverture de la gallerie photo")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        pickImages.launch(intent)
    }

    private fun openGalleryForExistingTrip(folderName: String) {
        Log.d(TAG, "ouverture de la gallerie photo")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        // Ajouter le nom du dossier en tant que donnée supplémentaire
        intent.putExtra("folderName", folderName)

        pickImages.launch(intent)
    }

    private fun uploadImagesToFirebase(selectedImagesUriList: List<Uri>, tripName: String) {
        Log.d(TAG, "lancement de l'upload")
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference
        val tripFolderRef = storageRef.child(tripName)

        selectedImagesUriList.forEachIndexed { index, uri ->
            val imageRef = tripFolderRef.child("image_$index.jpg")

            imageRef.putFile(uri)
                .addOnSuccessListener { uploadTask ->
                    uploadTask.storage.downloadUrl.addOnSuccessListener { downloadUri ->
                        val photoUrl = downloadUri.toString()
                        Log.d(TAG, "Image uploaded successfully. Download URL: $photoUrl")
                        storePhotoMetadata(uri.toString(), photoUrl)
                    }.addOnFailureListener { exception ->
                        Log.e(TAG, "Error getting download URL: ${exception.message}")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error uploading image: ${e.message}")
                }
        }
    }

    private fun storePhotoMetadata(photoPath: String, photoUrl: String?) {
        val uri = Uri.parse(photoPath)
        val coordinates = extractGPSFromPhoto(uri)
        coordinates?.let { (latitude, longitude) ->
            // Créer un objet contenant l'URL de l'image et les coordonnées GPS
            val photoData = hashMapOf(
                "url" to photoUrl,
                "coordinates" to hashMapOf(
                    "latitude" to latitude.toString(),
                    "longitude" to longitude.toString()
                )
            )
            // Ajouter les données de la photo à Firestore dans la collection "photos"
            val database = FirebaseFirestore.getInstance()
            database.collection("photos").add(photoData)
                .addOnSuccessListener {
                    Log.d(TAG, "Photo metadata added to Firestore successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error adding photo metadata to Firestore: ${e.message}")
                }
        }
    }
    private fun extractGPSFromPhoto(uri: Uri): Pair<Double, Double>? {
        Log.d(TAG, "extraction données GPS")
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    val latitude = latLong[0].toDouble()
                    val longitude = latLong[1].toDouble()
                    Log.d(TAG, "${Pair(latitude, longitude)}")
                    return Pair(latitude, longitude)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting GPS from photo: ${e.message}")
        }
        return null
    }

    private fun refreshFragment() {
        // Obtenez le gestionnaire de fragment et commencez une transaction
        val fragmentManager = requireActivity().supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        // Remplacez le fragment actuel par un nouveau fragment Trip
        val newFragment = Trip()
        fragmentTransaction.replace(R.id.fragment_trip, newFragment)
        // Ajoutez la transaction à la pile de retour pour permettre un retour en arrière
        fragmentTransaction.addToBackStack(null)
        // Validez la transaction pour effectuer le remplacement
        fragmentTransaction.commit()
    }

    private fun showPopupMenu(tripName: String, anchorView: View) {
        val popupMenu = PopupMenu(requireContext(), anchorView)
        popupMenu.inflate(R.menu.option_menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_rename_folder -> {
                    renameTripDialog()
                    true
                }
                R.id.menu_delete_folder -> {
                    deleteFolder(tripName)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun previewPhotosClick(tripName: String) {
        val previewPhotosFragment = PreviewPhotosFragment()
        val bundle = Bundle()
        bundle.putString("tripName", tripName)
        previewPhotosFragment.arguments = bundle
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_trip, previewPhotosFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun addPhotosClick(tripName: String) {
        openGalleryForExistingTrip(tripName)
    }

    private fun deleteFolder(tripName: String) {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference
        val tripFolderRef = storageRef.child(tripName)
        tripFolderRef.delete()
            .addOnSuccessListener {
                // Dossier supprimé avec succès
                Log.d(TAG, "Dossier supprimé avec succès: $tripName")
                // Mettre à jour l'interface utilisateur ou effectuer d'autres opérations
            }
            .addOnFailureListener { e ->
                // Erreur lors de la suppression du dossier
                Log.e(TAG, "Erreur lors de la suppression du dossier $tripName: ${e.message}")
                // Afficher un message d'erreur à l'utilisateur ou gérer l'erreur
            }
    }
    private fun renameFolder(tripName: String, newTripName: String) {}


}


