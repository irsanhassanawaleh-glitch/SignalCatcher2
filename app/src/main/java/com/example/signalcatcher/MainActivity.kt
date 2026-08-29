package com.example.signalcatcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellInfoGsm
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var tvOperator: TextView
    private lateinit var tvNetworkType: TextView
    private lateinit var tvSignalDbm: TextView
    private lateinit var tvSignalLevel: TextView
    private lateinit var tvAdvice: TextView
    private lateinit var recyclerCells: RecyclerView
    private lateinit var cellAdapter: CellAdapter
    private lateinit var btnRefresh: Button

    private var legacyPhoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val permissionRequestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        tvOperator = findViewById(R.id.tvOperator)
        tvNetworkType = findViewById(R.id.tvNetworkType)
        tvSignalDbm = findViewById(R.id.tvSignalDbm)
        tvSignalLevel = findViewById(R.id.tvSignalLevel)
        tvAdvice = findViewById(R.id.tvAdvice)
        btnRefresh = findViewById(R.id.btnRefresh)

        recyclerCells = findViewById(R.id.recyclerCells)
        recyclerCells.layoutManager = LinearLayoutManager(this)
        cellAdapter = CellAdapter(emptyList())
        recyclerCells.adapter = cellAdapter

        btnRefresh.setOnClickListener { refreshAll() }

        if (hasPermissions()) {
            startListening()
            refreshAll()
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions, permissionRequestCode)
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (hasPermissions()) {
                startListening()
                refreshAll()
            } else {
                Toast.makeText(
                    this,
                    "Les permissions Téléphone et Localisation sont nécessaires pour lire le signal",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    updateSignalUi(signalStrength.dbm, signalStrength.level)
                    updateCellList()
                }
            }
            telephonyManager.registerTelephonyCallback(mainExecutor, telephonyCallback as TelephonyCallback)
        } else {
            legacyPhoneStateListener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    updateSignalUi(signalStrength.dbm, signalStrength.level)
                    updateCellList()
                }
            }
            telephonyManager.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshAll() {
        if (!hasPermissions()) return

        tvOperator.text = "Opérateur : ${telephonyManager.networkOperatorName.ifBlank { "Inconnu" }}"
        tvNetworkType.text = "Type de réseau : ${networkTypeLabel(telephonyManager.dataNetworkType)}"

        val signalStrength = telephonyManager.signalStrength
        if (signalStrength != null) {
            updateSignalUi(signalStrength.dbm, signalStrength.level)
        }
        updateCellList()
    }

    @SuppressLint("MissingPermission")
    private fun updateCellList() {
        if (!hasPermissions()) return
        val cells: List<CellInfo> = try {
            telephonyManager.allCellInfo ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
        val rows = cells.mapNotNull { cellToRow(it) }
        cellAdapter.updateData(rows)
    }

    private fun cellToRow(cell: CellInfo): CellRow? {
        return when (cell) {
            is CellInfoLte -> CellRow(
                "LTE (4G)",
                cell.cellSignalStrength.dbm,
                cell.cellSignalStrength.level
            )
            is CellInfoNr -> CellRow(
                "NR (5G)",
                cell.cellSignalStrength.dbm,
                cell.cellSignalStrength.level
            )
            is CellInfoWcdma -> CellRow(
                "WCDMA (3G)",
                cell.cellSignalStrength.dbm,
                cell.cellSignalStrength.level
            )
            is CellInfoGsm -> CellRow(
                "GSM (2G)",
                cell.cellSignalStrength.dbm,
                cell.cellSignalStrength.level
            )
            else -> null
        }
    }

    private fun updateSignalUi(dbm: Int, level: Int) {
        tvSignalDbm.text = "Puissance : $dbm dBm"
        val levelLabel = when (level) {
            0 -> "Très faible"
            1 -> "Faible"
            2 -> "Moyen"
            3 -> "Bon"
            4 -> "Excellent"
            else -> "Inconnu"
        }
        tvSignalLevel.text = "Niveau : $levelLabel"
        tvAdvice.text = adviceFor(level)
    }

    private fun adviceFor(level: Int): String = when (level) {
        0, 1 -> "Signal faible. Essaie de te rapprocher d'une fenêtre ou d'un espace ouvert, " +
            "active l'appel Wi-Fi (Wi-Fi Calling) dans les paramètres réseau, ou déplace-toi " +
            "de quelques mètres pour voir si le niveau s'améliore."
        2 -> "Signal correct mais perfectible. Évite les sous-sols et zones bétonnées épaisses."
        3, 4 -> "Bon signal, rien à faire de particulier."
        else -> "Recherche du signal en cours..."
    }

    private fun networkTypeLabel(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_LTE -> "4G (LTE)"
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
        else -> "Inconnu"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        } else {
            legacyPhoneStateListener?.let {
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }
}

data class CellRow(val type: String, val dbm: Int, val level: Int)

class CellAdapter(private var items: List<CellRow>) :
    RecyclerView.Adapter<CellAdapter.CellViewHolder>() {

    class CellViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    fun updateData(newItems: List<CellRow>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): CellViewHolder {
        val tv = TextView(parent.context)
        tv.setPadding(24, 20, 24, 20)
        tv.textSize = 15f
        return CellViewHolder(tv)
    }

    override fun onBindViewHolder(holder: CellViewHolder, position: Int) {
        val row = items[position]
        holder.textView.text = "${row.type} — ${row.dbm} dBm (niveau ${row.level}/4)"
    }

    override fun getItemCount(): Int = items.size
    }
